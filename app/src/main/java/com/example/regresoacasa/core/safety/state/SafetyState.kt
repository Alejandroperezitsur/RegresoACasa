package com.example.regresoacasa.core.safety.state

import com.example.regresoacasa.core.safety.SafetyConstants

/**
 * State Machine de Seguridad
 * 
 * Define los estados posibles del sistema de seguridad.
 * El sistema SIEMPRE está en uno de estos estados.
 */
sealed class SafetyState {
    
    /**
     * Sistema inactivo
     * No hay navegación activa, no hay monitoreo
     */
    object Idle : SafetyState()
    
    /**
     * Sistema monitoreando
     * Guardian activo, tracking de ubicación básico
     */
    object Monitoring : SafetyState()
    
    /**
     * Sistema navegando
     * Navegación activa con ruta calculada
     */
    data class Navigating(
        val destination: String,
        val startTime: Long
    ) : SafetyState()
    
    /**
     * Alerta activada
     * Se ha enviado o está enviando una alerta de emergencia
     */
    data class AlertTriggered(
        val reason: String,
        val timestamp: Long
    ) : SafetyState()
    
    /**
     * Estado crítico
     * Sistema en modo degradación severa
     */
    data class Critical(
        val reason: String,
        val timestamp: Long
    ) : SafetyState()
    
    /**
     * Verifica si el estado es activo (no Idle)
     */
    fun isActive(): Boolean = this !is Idle
    
    /**
     * Verifica si el estado es crítico
     */
    fun isCritical(): Boolean = this is Critical
    
    /**
     * Verifica si hay navegación activa
     */
    fun isNavigating(): Boolean = this is Navigating
}

/**
 * Modos de degradación del sistema
 * 
 * Define cómo opera el sistema bajo diferentes condiciones.
 */
enum class SafetyMode {
    /**
     * Modo completo
     * GPS + Internet + todas las funcionalidades
     */
    FULL,
    
    /**
     * Sin internet
     * Solo GPS, sin rutas, solo tracking
     */
    NO_INTERNET,
    
    /**
     * GPS degradado
     * Precisión pobre, navegación deshabilitada
     */
    LOW_GPS,
    
    /**
     * Solo comunicación
     * Sin GPS, solo envío de alertas
     */
    SMS_ONLY,
    
    /**
     * Modo crítico
     * Solo fallback mínimo, alertas automáticas
     */
    CRITICAL
}

/**
 * Snapshot del estado del sistema para persistencia
 * 
 * Contiene toda la información necesaria para restaurar
 * el sistema después de un process death.
 */
data class SafetySnapshot(
    val state: SafetyState,
    val mode: SafetyMode,
    val lastLocation: LocationSnapshot?,
    val lastUpdate: Long,
    val batteryLevel: Int,
    val hasInternet: Boolean
) {
    /**
     * Verifica si el snapshot es válido para restauración
     */
    fun isValid(): Boolean {
        val age = System.currentTimeMillis() - lastUpdate
        return age < SafetyConstants.SNAPSHOT_TTL
    }
}

/**
 * Snapshot de ubicación para persistencia
 */
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val timestamp: Long,
    val isReliable: Boolean
)
