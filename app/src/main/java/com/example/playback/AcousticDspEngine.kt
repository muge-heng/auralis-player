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
 * Manages real 10-band hardware DSP attached directly to ExoPlayer's audioSessionId.
 * Uses Android 9+ (API 28+) DynamicsProcessing 10-band PreEq when available,
 * with seamless fallback to android.media.audiofx.Equalizer + BassBoost + Virtualizer.
 */
object AcousticDspEngine {
    private var attachedSessionId: Int = 0
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
        if (audioSessionId == attachedSessionId && (dynamicsProcessing != null || equalizer != null)) return
        releaseEffectsOnly()
        attachedSessionId = audioSessionId
        if (!lastState.enabled) {
            return
        }
        initializeEffects(audioSessionId)
        applyState(lastState)
    }

    @Synchronized
    private fun initializeEffects(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        // 1. Prefer native 10-band DynamicsProcessing on API 28+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
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
                dynamicsProcessing = dp
                Log.d(TAG, "Attached true 10-band DynamicsProcessing to audioSessionId=$audioSessionId")
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

        bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
    }

    @Synchronized
    fun applyState(state: EqState) {
        val normalizedBands = normalizeEqBands(state.bandLevelsDb, EQ_BAND_COUNT)
        val normalizedState = state.copy(bandLevelsDb = normalizedBands)
        lastState = normalizedState

        if (!normalizedState.enabled) {
            releaseEffectsOnly()
            return
        }
        if (dynamicsProcessing == null && equalizer == null && attachedSessionId > 0) {
            initializeEffects(attachedSessionId)
        }

        // Apply to 10-band DynamicsProcessing if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing?.let { dp ->
                try {
                    val preEq = DynamicsProcessing.Eq(true, true, EQ_BAND_COUNT)
                    for (i in 0 until EQ_BAND_COUNT) {
                        preEq.getBand(i).apply {
                            isEnabled = true
                            cutoffFrequency = StudioEqPresets.bandCutoffHz[i]
                            gain = normalizedBands[i].coerceIn(-12f, 12f)
                        }
                    }
                    dp.setPreEqAllChannelsTo(preEq)
                    dp.enabled = true
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed applying 10-band DynamicsProcessing: ${t.message}")
                }
            }
        }

        // Otherwise apply averaged bands to fallback Equalizer
        if (dynamicsProcessing == null) {
            equalizer?.let { eq ->
                try {
                    eq.enabled = true
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

        try {
            bassBoost?.let { bb ->
                bb.enabled = normalizedState.bassBoostPercent > 0
                if (bb.strengthSupported) {
                    val strength = (normalizedState.bassBoostPercent * 10).coerceIn(0, 1000).toShort()
                    bb.setStrength(strength)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed setting BassBoost: ${t.message}")
        }

        try {
            virtualizer?.let { virt ->
                virt.enabled = normalizedState.virtualizerPercent > 0
                if (virt.strengthSupported) {
                    val strength = (normalizedState.virtualizerPercent * 10).coerceIn(0, 1000).toShort()
                    virt.setStrength(strength)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed setting Virtualizer: ${t.message}")
        }
    }

    @Synchronized
    private fun releaseEffectsOnly() {
        try { dynamicsProcessing?.release() } catch (_: Throwable) {}
        try { equalizer?.release() } catch (_: Throwable) {}
        try { bassBoost?.release() } catch (_: Throwable) {}
        try { virtualizer?.release() } catch (_: Throwable) {}
        dynamicsProcessing = null
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    @Synchronized
    fun release() {
        releaseEffectsOnly()
        attachedSessionId = 0
    }
}
