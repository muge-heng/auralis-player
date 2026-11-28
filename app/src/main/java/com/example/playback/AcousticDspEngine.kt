package com.example.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import com.example.model.EQ_BAND_COUNT
import com.example.model.EqPreset
import com.example.model.EqState
import com.example.model.normalizeEqBands

private const val TAG = "AcousticDspEngine"

object StudioEqPresets {
    val bandLabels = listOf(
        "31Hz", "62Hz", "125Hz", "250Hz", "500Hz",
        "1kHz", "2kHz", "4kHz", "8kHz", "16kHz"
    )
    val bandCutoffHz = listOf(
        45f, 90f, 180f, 360f, 720f,
        1400f, 2800f, 5600f, 11200f, 20000f
    )

    val all = listOf(
        EqPreset("flat", "原声直出", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), 0, 0),
        EqPreset("warm", "黑胶暖声", listOf(3.0f, 3.5f, 2.5f, 1.5f, 0.5f, 0.0f, -0.5f, 0.0f, 1.0f, 1.5f), 25, 18),
        EqPreset("vocal", "人声空气", listOf(-2.0f, -1.5f, -0.5f, 0.5f, 2.0f, 3.5f, 4.0f, 3.5f, 2.5f, 2.0f), 10, 22),
        EqPreset("sub", "深潜低频", listOf(6.5f, 5.5f, 4.0f, 2.0f, 0.0f, -0.5f, 0.5f, 1.5f, 2.0f, 2.0f), 55, 25),
        EqPreset("classical", "音乐厅堂", listOf(2.5f, 2.0f, 1.0f, 0.0f, -0.5f, -0.5f, 1.0f, 2.5f, 3.5f, 4.5f), 12, 48),
        EqPreset("midnight", "深夜微醺", listOf(3.0f, 2.5f, 1.5f, 1.0f, 0.5f, 0.0f, -1.0f, -1.5f, -2.0f, -2.5f), 30, 35),
        EqPreset("electronic", "脉冲电音", listOf(5.5f, 4.5f, 2.5f, 0.5f, -1.5f, -1.0f, 1.5f, 3.5f, 4.5f, 5.0f), 45, 40),
        EqPreset("acg", "二次元晶透", listOf(2.5f, 2.0f, 0.5f, -0.5f, 1.0f, 2.5f, 3.5f, 4.0f, 4.5f, 3.5f), 20, 30)
    )
}

/**
 * Computes conservative negative pre-amp headroom (in dB, <= 0f) to keep boosted 10-band EQ
 * and BassBoost curves below 0 dBFS so AudioFlinger's DynamicsProcessing limiter never pumps
 * or stalls on loud mastered FLAC / WAV tracks.
 */
fun computeAutoPreampHeadroomDb(
    bandLevelsDb: List<Float>,
    bassBoostPercent: Int = 0
): Float {
    val maxPositiveBand = (bandLevelsDb.maxOrNull() ?: 0f).coerceIn(0f, 12f)
    val bassPenalty = (bassBoostPercent.coerceIn(0, 100) / 100f) * 2.0f
    val rawAttenuation = maxPositiveBand * 0.65f + bassPenalty
    if (rawAttenuation <= 0.05f) return 0f
    return -rawAttenuation.coerceIn(0f, 10f)
}

/**
 * Manages real 10-band hardware DSP attached directly to ExoPlayer's audioSessionId.
 * Uses Android 9+ (API 28+) DynamicsProcessing 10-band PreEq in low-latency time-resolution
 * mode when available (preventing FFT overlap-add underrun stutters on Hi-Res 192kHz FLAC / WAV),
 * with seamless fallback to android.media.audiofx.Equalizer and lazy-attached BassBoost / Virtualizer.
 */
object AcousticDspEngine {
    private var attachedSessionId: Int = 0
    private var appliedSessionId: Int = 0
    private var appliedState: EqState? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var lastState: EqState = EqState()

    val isTrueTenBandHardware: Boolean
        get() = dynamicsProcessing != null

    @Synchronized
    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId == attachedSessionId && (!lastState.enabled || dynamicsProcessing != null || equalizer != null)) {
            return
        }
        releaseEffectsOnly()
        attachedSessionId = audioSessionId
        if (!lastState.enabled) {
            return
        }
        initializeCoreEq(audioSessionId)
        applyStateInternal(lastState, forceReapply = true)
    }

    @Synchronized
    private fun initializeCoreEq(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        // 1. Prefer native 10-band DynamicsProcessing on API 28+ with VARIANT_FAVOR_TIME_RESOLUTION
        //    to keep AudioFlinger DSP latency & CPU spikes minimal (prevents continuous stutter on Hi-Res streams).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                    2,
                    true,
                    EQ_BAND_COUNT,
                    false,
                    0,
                    false,
                    0,
                    true
                ).build()
                val dp = DynamicsProcessing(0, audioSessionId, config)
                val preEq = DynamicsProcessing.Eq(true, true, EQ_BAND_COUNT)
                for (i in 0 until EQ_BAND_COUNT) {
                    preEq.getBand(i).apply {
                        isEnabled = true
                        cutoffFrequency = StudioEqPresets.bandCutoffHz[i]
                        gain = 0f
                    }
                }
                dp.setPreEqAllChannelsTo(preEq)
                // Configure an explicit gentle safety limiter to avoid harsh default threshold pumping
                val safetyLimiter = DynamicsProcessing.Limiter(
                    /* inUse = */ true,
                    /* enabled = */ true,
                    /* linkGroup = */ 0,
                    /* attackTime = */ 1.0f,
                    /* releaseTime = */ 60.0f,
                    /* ratio = */ 8.0f,
                    /* threshold = */ -0.5f,
                    /* postGain = */ 0.0f
                )
                dp.setLimiterAllChannelsTo(safetyLimiter)
                dp.setInputGainAllChannelsTo(0f)
                dynamicsProcessing = dp
                Log.d(TAG, "Attached low-latency 10-band DynamicsProcessing to audioSessionId=$audioSessionId")
            } catch (t: Throwable) {
                Log.w(TAG, "DynamicsProcessing unavailable, falling back to Equalizer: ${t.message}")
                try { dynamicsProcessing?.release() } catch (_: Throwable) {}
                dynamicsProcessing = null
            }
        }

        // 2. Fallback to standard Equalizer if DynamicsProcessing unavailable
        if (dynamicsProcessing == null) {
            try {
                equalizer = Equalizer(0, audioSessionId)
                Log.d(TAG, "Attached fallback hardware Equalizer to audioSessionId=$audioSessionId")
            } catch (t: Throwable) {
                Log.w(TAG, "Hardware Equalizer unavailable: ${t.message}")
                equalizer = null
            }
        }
    }

    @Synchronized
    fun applyState(state: EqState) {
        applyStateInternal(state, forceReapply = false)
    }

    @Synchronized
    private fun applyStateInternal(state: EqState, forceReapply: Boolean) {
        val normalizedBands = normalizeEqBands(state.bandLevelsDb, EQ_BAND_COUNT)
        val normalizedState = state.copy(
            bandLevelsDb = normalizedBands,
            bassBoostPercent = state.bassBoostPercent.coerceIn(0, 100),
            virtualizerPercent = state.virtualizerPercent.coerceIn(0, 100)
        )
        lastState = normalizedState

        if (!normalizedState.enabled) {
            releaseEffectsOnly()
            return
        }

        if (attachedSessionId <= 0) return

        // Skip redundant AudioEffect IPC calls when identical state is already active on this session
        if (!forceReapply &&
            appliedSessionId == attachedSessionId &&
            appliedState == normalizedState &&
            (dynamicsProcessing != null || equalizer != null)
        ) {
            return
        }

        val previouslyApplied = if (appliedSessionId == attachedSessionId) appliedState else null

        if (dynamicsProcessing == null && equalizer == null) {
            initializeCoreEq(attachedSessionId)
        }

        // Apply to 10-band DynamicsProcessing if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing?.let { dp ->
                try {
                    val headroomDb = computeAutoPreampHeadroomDb(
                        bandLevelsDb = normalizedBands,
                        bassBoostPercent = normalizedState.bassBoostPercent
                    )
                    dp.setInputGainAllChannelsTo(headroomDb)

                    if (previouslyApplied == null || forceReapply) {
                        val preEq = DynamicsProcessing.Eq(true, true, EQ_BAND_COUNT)
                        for (i in 0 until EQ_BAND_COUNT) {
                            preEq.getBand(i).apply {
                                isEnabled = true
                                cutoffFrequency = StudioEqPresets.bandCutoffHz[i]
                                gain = normalizedBands[i].coerceIn(-12f, 12f)
                            }
                        }
                        dp.setPreEqAllChannelsTo(preEq)
                    } else {
                        val prevBands = previouslyApplied.bandLevelsDb
                        for (i in 0 until EQ_BAND_COUNT) {
                            val targetGain = normalizedBands[i].coerceIn(-12f, 12f)
                            val prevGain = prevBands.getOrNull(i)
                            if (prevGain == null || prevGain != targetGain) {
                                val band = DynamicsProcessing.EqBand(
                                    true,
                                    StudioEqPresets.bandCutoffHz[i],
                                    targetGain
                                )
                                dp.setPreEqBandAllChannelsTo(i, band)
                            }
                        }
                    }
                    if (!dp.enabled) {
                        dp.enabled = true
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed applying 10-band DynamicsProcessing: ${t.message}")
                }
            }
        }

        // Otherwise apply averaged bands to fallback Equalizer
        if (dynamicsProcessing == null) {
            equalizer?.let { eq ->
                try {
                    if (!eq.enabled) {
                        eq.enabled = true
                    }
                    val numBands = eq.numberOfBands.toInt().coerceAtLeast(1)
                    val range = eq.bandLevelRange
                    val minMb = range[0].toInt()
                    val maxMb = range[1].toInt()

                    for (i in 0 until numBands) {
                        val startIdx = ((i * EQ_BAND_COUNT) / numBands).coerceIn(0, EQ_BAND_COUNT - 1)
                        val endIdx = (((i + 1) * EQ_BAND_COUNT) / numBands).coerceIn(startIdx + 1, EQ_BAND_COUNT)
                        val avgDb = normalizedBands.subList(startIdx, endIdx).average().toFloat()
                        val targetMb = (avgDb * 100f).toInt().coerceIn(minMb, maxMb).toShort()
                        eq.setBandLevel(i.toShort(), targetMb)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed setting fallback EQ bands: ${t.message}")
                }
            }
        }

        // Lazy-attach BassBoost ONLY when > 0% so AudioFlinger isn't burdened by idle stacked effects
        try {
            if (normalizedState.bassBoostPercent > 0) {
                val bb = bassBoost ?: runCatching { BassBoost(0, attachedSessionId) }.getOrNull().also {
                    bassBoost = it
                }
                bb?.let {
                    if (it.strengthSupported) {
                        val strength = (normalizedState.bassBoostPercent * 10).coerceIn(0, 1000).toShort()
                        it.setStrength(strength)
                    }
                    if (!it.enabled) {
                        it.enabled = true
                    }
                }
            } else {
                releaseBassBoost()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed setting BassBoost: ${t.message}")
        }

        // Lazy-attach Virtualizer ONLY when > 0% so AudioFlinger isn't burdened by idle stacked effects
        try {
            if (normalizedState.virtualizerPercent > 0) {
                val virt = virtualizer ?: runCatching { Virtualizer(0, attachedSessionId) }.getOrNull().also {
                    virtualizer = it
                }
                virt?.let {
                    if (it.strengthSupported) {
                        val strength = (normalizedState.virtualizerPercent * 10).coerceIn(0, 1000).toShort()
                        it.setStrength(strength)
                    }
                    if (!it.enabled) {
                        it.enabled = true
                    }
                }
            } else {
                releaseVirtualizer()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed setting Virtualizer: ${t.message}")
        }

        appliedSessionId = attachedSessionId
        appliedState = normalizedState
    }

    private fun releaseBassBoost() {
        try { bassBoost?.release() } catch (_: Throwable) {}
        bassBoost = null
    }

    private fun releaseVirtualizer() {
        try { virtualizer?.release() } catch (_: Throwable) {}
        virtualizer = null
    }

    @Synchronized
    private fun releaseEffectsOnly() {
        try { dynamicsProcessing?.release() } catch (_: Throwable) {}
        try { equalizer?.release() } catch (_: Throwable) {}
        releaseBassBoost()
        releaseVirtualizer()
        dynamicsProcessing = null
        equalizer = null
        appliedSessionId = 0
        appliedState = null
    }

    @Synchronized
    fun release() {
        releaseEffectsOnly()
        attachedSessionId = 0
    }
}
