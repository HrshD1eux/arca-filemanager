package com.Hrshd1eux.filemanager.core.designsystem

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import com.Hrshd1eux.filemanager.core.settings.preferences.AppSettingsRepository

/**
 * Provides tactile haptic vibration and subtle sound feedback when selecting files
 * or triggering selection actions across ARCA.
 */
object SelectionHaptics {

    /**
     * Play selection feedback (crisp tactile vibration + subtle click sound).
     */
    fun playSelectionFeedback(context: Context? = null, view: View? = null) {
        val appSettings = AppSettingsRepository.getInstance(context)
        val hapticsEnabled = appSettings.hapticFeedback.value
        val soundEnabled = appSettings.soundFeedback.value

        if (hapticsEnabled) {
            // 1. Perform view-level tactile tick
            view?.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )

            // 2. Hardware vibrator for guaranteed crisp tactile click
            context?.let { ctx ->
                runCatching {
                    val vibrator = getVibrator(ctx)
                    vibrator?.let { vib ->
                        if (vib.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vib.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vib.vibrate(20)
                            }
                        }
                    }
                }
            }
        }

        if (soundEnabled) {
            view?.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    /**
     * Play heavier tactile feedback when entering selection mode or long-pressing an item.
     */
    fun playLongPressFeedback(context: Context? = null, view: View? = null) {
        val appSettings = AppSettingsRepository.getInstance(context)
        val hapticsEnabled = appSettings.hapticFeedback.value
        val soundEnabled = appSettings.soundFeedback.value

        if (hapticsEnabled) {
            view?.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )

            context?.let { ctx ->
                runCatching {
                    val vibrator = getVibrator(ctx)
                    vibrator?.let { vib ->
                        if (vib.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vib.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vib.vibrate(40)
                            }
                        }
                    }
                }
            }
        }

        if (soundEnabled) {
            view?.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
