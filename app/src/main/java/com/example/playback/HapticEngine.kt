package com.example.playback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.HapticProfile

class HapticEngine(context: Context) {

    @Volatile
    var profile: HapticProfile = HapticProfile.STUDIO

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** Crisp mechanical detent for scrubbing, EQ sliders, lyric line taps */
    fun tickLight() {
        if (profile == HapticProfile.OFF) return
        val amp = if (profile == HapticProfile.STUDIO) 48 else 26
        vibrateOneShot(10L, amp)
    }

    /** Weighted spring thump for Play/Pause, sheet expansion, skip track */
    fun springImpact() {
        if (profile == HapticProfile.OFF) return
        val amp = if (profile == HapticProfile.STUDIO) 115 else 60
        vibrateOneShot(18L, amp)
    }

    /** Double-beat cardiac pulse when favoriting a track */
    fun heartPulse() {
        if (profile == HapticProfile.OFF) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val scale = if (profile == HapticProfile.STUDIO) 1.0f else 0.55f
                val timings = longArrayOf(0L, 18L, 65L, 26L)
                val amps = intArrayOf(0, (110 * scale).toInt(), 0, (175 * scale).toInt())
                vib.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0L, 18L, 65L, 26L), -1)
            }
        }
    }

    /** Ascending confirmation pulse when scan or AI analysis completes */
    fun harmonicChime() {
        if (profile == HapticProfile.OFF) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val scale = if (profile == HapticProfile.STUDIO) 1.0f else 0.55f
                val timings = longArrayOf(0L, 12L, 45L, 16L, 45L, 22L)
                val amps = intArrayOf(
                    0,
                    (60 * scale).toInt(),
                    0,
                    (105 * scale).toInt(),
                    0,
                    (155 * scale).toInt()
                )
                vib.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(30L)
            }
        }
    }

    private fun vibrateOneShot(durationMs: Long, amplitude: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }
}
