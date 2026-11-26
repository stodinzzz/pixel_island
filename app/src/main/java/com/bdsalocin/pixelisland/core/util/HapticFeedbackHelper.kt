package com.bdsalocin.pixelisland.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Gestionnaire de retour haptique haute fidelite type Taptic Engine pour Pixel Island.
 * Optimise pour le moteur lineaire du Google Pixel 8 Pro sous Android 14+.
 */
object HapticFeedbackHelper {

    /**
     * Micro vibration seche et legere style iPhone lors d'un tap sur la capsule Dynamic Island au repos.
     */
    fun performIslandBounceFeedback(context: Context, view: View? = null) {
        if (tryPrimitiveClick(context, scale = 0.52f)) {
            return
        }
        val viewFeedbackHandled = try {
            view?.performHapticFeedback(
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            ) ?: false
        } catch (_: Exception) {
            false
        }
        if (!viewFeedbackHandled) {
            vibratePredefined(context, VibrationEffect.EFFECT_TICK, fallbackDurationMs = 12L)
        }
    }

    /**
     * Vibration tactile nette (style Taptic Engine) lors de l'ouverture de la carte musicale au clic.
     */
    fun performTapticFeedback(context: Context, view: View? = null) {
        if (tryPrimitiveClick(context, scale = 0.72f)) {
            return
        }
        val viewFeedbackHandled = try {
            view?.performHapticFeedback(
                HapticFeedbackConstants.CONTEXT_CLICK,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            ) ?: false
        } catch (_: Exception) {
            false
        }
        if (!viewFeedbackHandled) {
            vibratePredefined(context, VibrationEffect.EFFECT_CLICK, fallbackDurationMs = 18L)
        }
    }

    /**
     * Vibration tactile de confirmation pour la fermeture de la carte vers l'ilot compact.
     */
    fun performConfirmationFeedback(context: Context, view: View? = null) {
        if (tryPrimitiveClick(context, scale = 0.85f)) {
            return
        }
        val viewFeedbackHandled = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view?.performHapticFeedback(
                    HapticFeedbackConstants.CONFIRM,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                ) ?: false
            } else {
                view?.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                ) ?: false
            }
        } catch (_: Exception) {
            false
        }

        if (!viewFeedbackHandled) {
            vibratePredefined(context, VibrationEffect.EFFECT_HEAVY_CLICK, fallbackDurationMs = 26L)
        }
    }

    private fun tryPrimitiveClick(context: Context, scale: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val vibrator = getVibrator(context) ?: return false
            if (!vibrator.hasVibrator()) return false
            val supported = vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            if (supported) {
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale.coerceIn(0.1f, 1.0f))
                    .compose()
                vibrator.vibrate(composition)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun vibratePredefined(context: Context, effectId: Int, fallbackDurationMs: Long) {
        try {
            val vibrator = getVibrator(context)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(effectId))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(fallbackDurationMs)
                }
            }
        } catch (_: Exception) {}
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
