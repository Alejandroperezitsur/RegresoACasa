package com.example.regresoacasa.core.safety.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * V3 FASE 6 — ENERGY-AWARE SYSTEM
 * 
 * Sistema adaptativo que ajusta la frecuencia de operaciones según el nivel de batería.
 * Evita que el sistema agote la batería del usuario.
 */
class EnergyManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    
    private val _powerMode = MutableStateFlow(PowerMode.NORMAL)
    val powerMode: StateFlow<PowerMode> = _powerMode.asStateFlow()
    
    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()
    
    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()
    
    /**
     * Modo de energía
     */
    enum class PowerMode {
        /**
         * Modo normal - frecuencia alta
         */
        NORMAL,
        
        /**
         * Modo ahorro - frecuencia media
         */
        LOW_POWER,
        
        /**
         * Modo crítico - frecuencia mínima
         */
        CRITICAL
    }
    
    /**
     * Configuración de frecuencia según modo
     */
    data class FrequencyConfig(
        val gpsIntervalMs: Long,
        val watchdogIntervalMs: Long,
        val heartbeatIntervalMs: Long,
        val persistenceIntervalMs: Long
    )
    
    /**
     * Obtiene la configuración de frecuencia actual
     */
    fun getCurrentConfig(): FrequencyConfig {
        return when (_powerMode.value) {
            PowerMode.NORMAL -> FrequencyConfig(
                gpsIntervalMs = 3000L,
                watchdogIntervalMs = 10000L,
                heartbeatIntervalMs = 30000L,
                persistenceIntervalMs = 30000L
            )
            PowerMode.LOW_POWER -> FrequencyConfig(
                gpsIntervalMs = 10000L,
                watchdogIntervalMs = 30000L,
                heartbeatIntervalMs = 60000L,
                persistenceIntervalMs = 60000L
            )
            PowerMode.CRITICAL -> FrequencyConfig(
                gpsIntervalMs = 30000L,
                watchdogIntervalMs = 60000L,
                heartbeatIntervalMs = 120000L,
                persistenceIntervalMs = 120000L
            )
        }
    }
    
    /**
     * Inicia el monitoreo de batería
     */
    fun startMonitoring() {
        updateBatteryStatus()
        
        // Actualizar cada 30 segundos
        scope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(30000)
                updateBatteryStatus()
            }
        }
    }
    
    /**
     * Actualiza el estado de la batería
     */
    private fun updateBatteryStatus() {
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val chargingStatus = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        
        _batteryLevel.value = level
        _isCharging.value = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                           chargingStatus == BatteryManager.BATTERY_STATUS_FULL
        
        // Actualizar modo de energía
        updatePowerMode()
    }
    
    /**
     * Actualiza el modo de energía según el nivel de batería
     */
    private fun updatePowerMode() {
        val level = _batteryLevel.value
        val charging = _isCharging.value
        
        val newMode = when {
            charging -> PowerMode.NORMAL
            level <= 10 -> PowerMode.CRITICAL
            level <= 20 -> PowerMode.LOW_POWER
            else -> PowerMode.NORMAL
        }
        
        if (newMode != _powerMode.value) {
            _powerMode.value = newMode
        }
    }
    
    /**
     * Verifica si el sistema está en modo crítico
     */
    fun isCriticalMode(): Boolean = _powerMode.value == PowerMode.CRITICAL
    
    /**
     * Verifica si el sistema está en modo ahorro
     */
    fun isLowPowerMode(): Boolean = _powerMode.value == PowerMode.LOW_POWER
    
    /**
     * Obtiene el tiempo estimado restante de batería (en minutos)
     * Estimación aproximada basada en nivel actual
     */
    fun getEstimatedTimeRemaining(): Int {
        val level = _batteryLevel.value
        if (_isCharging.value) return Int.MAX_VALUE
        
        // Estimación muy aproximada: 1% = 3 minutos en uso normal
        return level * 3
    }
    
    /**
     * Verifica si debería reducir actividad
     */
    fun shouldReduceActivity(): Boolean {
        return _powerMode.value != PowerMode.NORMAL
    }
    
    /**
     * Verifica si debería detener actividad no crítica
     */
    fun shouldStopNonCriticalActivity(): Boolean {
        return _powerMode.value == PowerMode.CRITICAL
    }
}
