package com.example.regresoacasa.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log

/**
 * SafeHaptics - Sistema de vibraciones determinista, respetuoso del sistema, sin spam y coherente en UX
 * FASES IMPLEMENTADAS:
 * - FASE 2: Respeta configuración del sistema (Settings.System.HAPTIC_FEEDBACK_ENABLED)
 * - FASE 3: Rate limiting global (300ms mínimo entre vibraciones)
 * - FASE 4: Throttle específico GPS lost (10s máximo)
 * - FASE 5: Sistema de prioridades (LOW, MEDIUM, HIGH, CRITICAL)
 */
class SafeHaptics(private val context: Context) {

    companion object {
        private const val TAG = "SafeHaptics"
        private const val MIN_INTERVAL_MS = 300L // FASE 3: Rate limiting global
        private const val GPS_LOST_INTERVAL = 10000L // FASE 4: Throttle GPS lost
    }

    // FASE 5: Sistema de prioridades
    enum class HapticPriority {
        LOW,        // 100m - giro lejano
        MEDIUM,     // 50m - giro medio
        HIGH,       // 20m - giro cercano
        CRITICAL    // desviación, emergencia - no puede ser interrumpido
    }

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing vibrator", e)
            null
        }
    }

    // FASE 3: Rate limiting
    private var lastVibrationTime = 0L

    // FASE 4: Throttle específico GPS lost
    private var lastGpsLostAt = 0L

    // FASE 5: Control de prioridad
    private var currentPriority = HapticPriority.LOW

    val isAvailable: Boolean
        get() = vibrator?.hasVibrator() == true && isSystemHapticsEnabled()

    /**
     * FASE 2: Verificar configuración del sistema
     * Si el usuario desactivó vibraciones en ajustes del sistema, respetarlo
     */
    private fun isSystemHapticsEnabled(): Boolean {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1 // Default: habilitado
            ) == 1
        } catch (e: Exception) {
            // Si no podemos leer, asumir habilitado para no bloquear funcionalidad
            Log.w(TAG, "No se pudo leer configuración de haptics, asumiendo habilitado", e)
            true
        }
    }

    /**
     * FASE 3: Rate limiting global
     * Nunca más de 1 vibración cada 300ms
     */
    private fun canVibrate(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastVibrationTime < MIN_INTERVAL_MS) {
            Log.d(TAG, "Rate limit: ignorando vibración (debounce)")
            return false
        }
        lastVibrationTime = now
        return true
    }

    /**
     * FASE 5: Verificar si la vibración debe ejecutarse según prioridad
     * CRITICAL no puede ser bloqueado, solo LOW/MEDIUM/HIGH
     */
    private fun shouldExecute(priority: HapticPriority): Boolean {
        // CRITICAL siempre pasa
        if (priority == HapticPriority.CRITICAL) return true
        // Los demás deben ser >= prioridad actual
        return priority.ordinal >= currentPriority.ordinal
    }

    /**
     * FASE 5: Actualizar prioridad actual
     */
    fun updatePriority(priority: HapticPriority) {
        currentPriority = priority
        Log.d(TAG, "Prioridad actualizada a: $priority")
    }

    /**
     * FASE 5: Reset de prioridad
     * Llamar después de: nueva instrucción, ruta recalculada, llegada
     */
    fun resetPriority() {
        currentPriority = HapticPriority.LOW
        Log.d(TAG, "Prioridad reseteada a LOW")
    }

    /**
     * Ejecuta vibración de forma segura con todas las protecciones:
     * - FASE 2: Respeta configuración del sistema
     * - FASE 3: Rate limiting global
     * - FASE 5: Sistema de prioridades
     * Nunca lanza excepción, nunca crashea la app
     */
    private fun vibrateSafely(pattern: HapticPattern, priority: HapticPriority = HapticPriority.MEDIUM) {
        // FASE 2: Verificar disponibilidad hardware y configuración sistema
        if (!isAvailable) {
            Log.w(TAG, "Vibrator not available or disabled in system settings, skipping haptic: ${pattern.name}")
            return
        }

        // FASE 3: Verificar rate limiting
        if (!canVibrate()) {
            return
        }

        // FASE 5: Verificar prioridad
        if (!shouldExecute(priority)) {
            Log.d(TAG, "Prioridad insuficiente para ${pattern.name} (actual: $currentPriority, requerida: $priority)")
            return
        }

        // Actualizar prioridad actual si es mayor
        if (priority.ordinal > currentPriority.ordinal) {
            updatePriority(priority)
        }

        try {
            when (pattern) {
                is HapticPattern.Single -> vibrateSingle(pattern.duration)
                is HapticPattern.Pattern -> vibratePattern(pattern.timings)
                is HapticPattern.Waveform -> vibrateWaveform(pattern.timings, pattern.amplitudes)
            }
            Log.d(TAG, "Haptic executed: ${pattern.name} (priority: $priority)")
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed for ${pattern.name}", e)
            // Silenciosamente falla - la app continúa
        }
    }

    // ============ PATRONES PREDEFINIDOS CON PRIORIDADES ============

    fun navigationStarted() = vibrateSafely(
        HapticPattern.Single("navigation_started", 100),
        HapticPriority.LOW
    )

    fun turnApproaching100m() = vibrateSafely(
        HapticPattern.Single("turn_100m", 150),
        HapticPriority.LOW
    )

    fun turnApproaching50m() = vibrateSafely(
        HapticPattern.Pattern("turn_50m", longArrayOf(0, 100, 100, 100)),
        HapticPriority.MEDIUM
    )

    fun turnApproaching20m() = vibrateSafely(
        HapticPattern.Pattern("turn_20m", longArrayOf(0, 80, 80, 80, 80, 80)),
        HapticPriority.HIGH
    )

    fun turnCompleted() = vibrateSafely(
        HapticPattern.Single("turn_completed", 200),
        HapticPriority.MEDIUM
    )

    // FASE 5: CRITICAL - no puede ser bloqueado por otros eventos
    fun offRouteDetected() = vibrateSafely(
        HapticPattern.Pattern("off_route", longArrayOf(0, 300, 100, 300, 100, 300)),
        HapticPriority.CRITICAL
    )

    fun routeRecalculated() = vibrateSafely(
        HapticPattern.Single("route_recalculated", 300),
        HapticPriority.MEDIUM
    )

    fun arrivalCelebration() = vibrateSafely(
        HapticPattern.Pattern("arrival", longArrayOf(0, 200, 150, 200, 150, 500, 150, 200)),
        HapticPriority.HIGH
    )

    /**
     * FASE 4: GPS Lost con throttle específico (10s)
     */
    fun gpsLost() {
        val now = System.currentTimeMillis()
        if (now - lastGpsLostAt < GPS_LOST_INTERVAL) {
            Log.d(TAG, "GPS lost throttled: ignorando (intervalo mínimo 10s)")
            return
        }
        lastGpsLostAt = now
        vibrateSafely(
            HapticPattern.Pattern("gps_lost", longArrayOf(0, 200, 100, 200)),
            HapticPriority.CRITICAL
        )
    }

    fun guardianCheckIn() = vibrateSafely(
        HapticPattern.Single("guardian_checkin", 50),
        HapticPriority.LOW
    )

    fun guardianAlert() = vibrateSafely(
        HapticPattern.Pattern("guardian_alert", longArrayOf(0, 100, 50, 100, 50, 100, 50, 100, 50, 100)),
        HapticPriority.CRITICAL
    )

    fun emergency() = vibrateSafely(
        HapticPattern.Waveform(
            "emergency",
            longArrayOf(0, 500, 100, 500, 100, 500, 100, 500, 100, 500),
            intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255)
        ),
        HapticPriority.CRITICAL
    )

    // ============ IMPLEMENTACIÓN PRIVADA ============

    private fun vibrateSingle(milliseconds: Long) {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(milliseconds)
            }
        }
    }

    private fun vibratePattern(timings: LongArray) {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        }
    }

    private fun vibrateWaveform(timings: LongArray, amplitudes: IntArray) {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        }
    }

    // ============ CLASES SEALED PARA PATRONES ============

    sealed class HapticPattern(val name: String) {
        class Single(name: String, val duration: Long) : HapticPattern(name)
        class Pattern(name: String, val timings: LongArray) : HapticPattern(name)
        class Waveform(name: String, val timings: LongArray, val amplitudes: IntArray) : HapticPattern(name)
    }
}
