package com.example.regresoacasa.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Manager de feedback háptico según especificación de producto v1.0
 * Cada patrón tiene significado semántico específico
 */
class HapticFeedbackManager(private val context: Context) {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * 1. Navegación iniciada: 1 pulso corto (100ms)
     * Prioridad: Baja
     */
    fun navigationStarted() {
        vibrateSingle(100)
    }

    /**
     * 2. 100m antes de giro: 1 pulso medio (150ms)
     * Prioridad: Media
     */
    fun turnApproaching100m() {
        vibrateSingle(150)
    }

    /**
     * 3. 50m antes de giro: 2 pulsos (100ms + 100ms)
     * Prioridad: Media
     */
    fun turnApproaching50m() {
        vibratePattern(longArrayOf(0, 100, 100, 100))
    }

    /**
     * 4. 20m antes de giro: 3 pulsos rápidos (80ms × 3)
     * Prioridad: Alta
     */
    fun turnApproaching20m() {
        vibratePattern(longArrayOf(0, 80, 80, 80, 80, 80))
    }

    /**
     * 5. Giro completado: 1 pulso largo suave (200ms)
     * Prioridad: Baja
     */
    fun turnCompleted() {
        vibrateSingle(200)
    }

    /**
     * 6. Desviación detectada: 3 pulsos fuertes (200ms × 3)
     * Prioridad: CRÍTICA
     * Patrón: Bzz-bzz-bzz (no confundir con giro inminente)
     */
    fun offRouteDetected() {
        vibratePattern(longArrayOf(0, 200, 100, 200, 100, 200))
    }

    /**
     * 7. Ruta recalculada: 1 pulso largo (300ms)
     * Prioridad: Media
     */
    fun routeRecalculated() {
        vibrateSingle(300)
    }

    /**
     * 8. Llegada: Celebración especial
     * Patrón: 2 cortas, 1 larga, 1 corta (200, 200, 500, 200)
     * Prioridad: Alta
     */
    fun arrivalCelebration() {
        vibratePattern(longArrayOf(0, 200, 150, 200, 150, 500, 150, 200))
    }

    /**
     * 9. GPS perdido: 2 pulsos cada 10s (200ms)
     * Prioridad: Media
     */
    fun gpsLost() {
        vibratePattern(longArrayOf(0, 100, 100, 100))
    }

    /**
     * 10. Check-in Guardian automático: 1 pulso sutil (50ms)
     * Prioridad: Baja
     */
    fun guardianCheckIn() {
        vibrateSingle(50)
    }

    /**
     * 11. Alerta Guardian (no respuesta): 5 pulsos rápidos (500ms total)
     * Prioridad: CRÍTICA
     */
    fun guardianAlert() {
        vibratePattern(longArrayOf(0, 100, 50, 100, 50, 100, 50, 100, 50, 100))
    }

    /**
     * 12. Emergencia: Vibración intensa continua por 3 segundos
     * Prioridad: MÁXIMA
     */
    fun emergency() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 500, 100, 500, 100, 500, 100, 500, 100, 500),
                intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255),
                -1
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 100, 500, 100, 500, 100, 500, 100, 500), -1)
        }
    }

    private fun vibrateSingle(milliseconds: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(milliseconds)
        }
    }

    private fun vibratePattern(timings: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    fun hasVibrator(): Boolean = vibrator.hasVibrator()
}
