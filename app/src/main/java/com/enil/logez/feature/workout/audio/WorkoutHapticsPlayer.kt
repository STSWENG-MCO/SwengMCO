package com.enil.logez.feature.workout.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE2_PLAN.md §9.7: `VIBRATE` is a normal (non-runtime) permission. "Vibration fires
 * regardless of sound volume settings (silent-gym mode)" — deliberately not gated by any
 * volume setting, unlike [WorkoutAudioPlayer].
 */
interface WorkoutHapticsPlayer {
    /** Distinct double-buzz for rest-timer completion. */
    fun vibrateRestEnd()

    /** Short click for set completion. */
    fun vibrateSetComplete()
}

@Singleton
class SystemWorkoutHapticsPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : WorkoutHapticsPlayer {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    override fun vibrateRestEnd() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 120, 100, 120), -1)
        vibrator.vibrate(effect)
    }

    override fun vibrateSetComplete() {
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }
}
