package com.axoo.core.haptics

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

object Haptics {
    @Suppress("DEPRECATION")
    private fun vibrate(view: View, durationMs: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
        val vibrator = view.context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    fun click(view: View) = vibrate(view, 10L, 35)

    // Use the same vibration path as the other confirmation actions in the app.
    fun refresh(view: View) = confirm(view)

    fun commentSent(view: View) = confirm(view)

    fun confirm(view: View) = vibrate(view, 20L, 60)

    fun reject(view: View) = vibrate(view, 40L, 100)
}
