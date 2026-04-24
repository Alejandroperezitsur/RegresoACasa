package com.example.regresoacasa.core.safety.watchdog

import android.content.Context
import android.util.Log
import com.example.regresoacasa.core.safety.SafetyConstants
import com.example.regresoacasa.core.safety.state.SafetyState
import com.example.regresoacasa.core.safety.state.SafetyMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Watchdog de Seguridad Autónomo
 * 
 * Monitorea el sistema de forma independiente y detecta fallos críticos.
 * Funciona incluso si la UI no está activa.
 */
class SafetyWatchdog(
    private val context: Context,
    private val scope: CoroutineScope
) {
    
    private val _watchdogState = MutableStateFlow<WatchdogState>(WatchdogState.Idle)
    val watchdogState: StateFlow<WatchdogState> = _watchdogState.asStateFlow()
    
    private var checkJob: Job? = null
    private var isRunning = false
    
    // Callback para acciones automáticas
    var onCriticalAlert: ((String) -> Unit)? = null
    var onModeChange: ((SafetyMode) -> Unit)? = null
    
    // Snapshot del sistema para evaluación
    private var currentSnapshot: SafetySnapshot? = null
    
    /**
     * Estado del watchdog
     */
    sealed class WatchdogState {
        object Idle : WatchdogState()
        object Monitoring : WatchdogState()
        data class Warning(val reason: String) : WatchdogState()
        data class Critical(val reason: String) : WatchdogState()
    }
    
    /**
     * Snapshot del sistema para evaluación
     */
    data class SafetySnapshot(
        val lastGpsUpdate: Long,
        val lastMonitorCycle: Long,
        val hasGpsSignal: Boolean,
        val gpsAccuracy: Float?,
        val batteryLevel: Int,
        val isNavigating: Boolean
    )
    
    /**
     * Resultado de evaluación del watchdog
     */
    sealed class WatchdogResult {
        object Healthy : WatchdogResult()
        data class Warning(val reason: String) : WatchdogResult()
        data class Critical(val reason: String, val action: () -> Unit) : WatchdogResult()
    }
    
    /**
     * Inicia el monitoreo del sistema
     */
    fun start() {
        if (isRunning) {
            Log.w("SafetyWatchdog", "Watchdog already running")
            return
        }
        
        isRunning = true
        _watchdogState.value = WatchdogState.Monitoring
        
        checkJob = scope.launch(Dispatchers.IO) {
            while (isRunning) {
                evaluateSystem()
                delay(SafetyConstants.WATCHDOG_CHECK_INTERVAL)
            }
        }
        
        Log.d("SafetyWatchdog", "Watchdog started")
    }
    
    /**
     * Detiene el monitoreo
     */
    fun stop() {
        isRunning = false
        checkJob?.cancel()
        _watchdogState.value = WatchdogState.Idle
        Log.d("SafetyWatchdog", "Watchdog stopped")
    }
    
    /**
     * Actualiza el snapshot del sistema
     */
    fun updateSnapshot(snapshot: SafetySnapshot) {
        currentSnapshot = snapshot
    }
    
    /**
     * Evalúa el estado del sistema
     */
    private fun evaluateSystem() {
        val snapshot = currentSnapshot ?: return
        
        val result = evaluate(snapshot)
        
        when (result) {
            is WatchdogResult.Healthy -> {
                _watchdogState.value = WatchdogState.Monitoring
            }
            is WatchdogResult.Warning -> {
                _watchdogState.value = WatchdogState.Warning(result.reason)
                Log.w("SafetyWatchdog", "WARNING: ${result.reason}")
            }
            is WatchdogResult.Critical -> {
                _watchdogState.value = WatchdogState.Critical(result.reason)
                Log.e("SafetyWatchdog", "CRITICAL: ${result.reason}")
                
                // Ejecutar acción automática
                result.action()
                
                // Notificar al sistema
                onCriticalAlert?.invoke(result.reason)
            }
        }
    }
    
    /**
     * Evalúa el snapshot y determina el resultado
     */
    fun evaluate(snapshot: SafetySnapshot): WatchdogResult {
        val now = System.currentTimeMillis()
        val timeSinceLastGps = if (snapshot.lastGpsUpdate > 0) {
            now - snapshot.lastGpsUpdate
        } else {
            Long.MAX_VALUE
        }
        
        val timeSinceLastCycle = if (snapshot.lastMonitorCycle > 0) {
            now - snapshot.lastMonitorCycle
        } else {
            Long.MAX_VALUE
        }
        
        // CRÍTICO: Sin GPS por más de 120 segundos
        if (timeSinceLastGps > SafetyConstants.GPS_TIMEOUT_CRITICAL) {
            return WatchdogResult.Critical(
                reason = "No GPS for ${timeSinceLastGps / 1000}s - possible device failure or shutdown",
                action = {
                    onCriticalAlert?.invoke("GPS timeout critical - possible emergency")
                }
            )
        }
        
        // CRÍTICO: Batería crítica durante navegación
        if (snapshot.batteryLevel <= SafetyConstants.BATTERY_CRITICAL_THRESHOLD && snapshot.isNavigating) {
            return WatchdogResult.Critical(
                reason = "Battery critical (${snapshot.batteryLevel}%) during navigation",
                action = {
                    onCriticalAlert?.invoke("Battery critical during navigation")
                }
            )
        }
        
        // WARNING: Sin GPS por más de 60 segundos
        if (timeSinceLastGps > SafetyConstants.GPS_TIMEOUT_WARNING) {
            return WatchdogResult.Warning(
                reason = "No GPS for ${timeSinceLastGps / 1000}s - signal lost"
            )
        }
        
        // WARNING: GPS degradado
        if (snapshot.gpsAccuracy != null && snapshot.gpsAccuracy > SafetyConstants.GPS_PRECISION_DEGRADED) {
            return WatchdogResult.Warning(
                reason = "GPS accuracy degraded (${snapshot.gpsAccuracy.toInt()}m)"
            )
        }
        
        // WARNING: Batería baja
        if (snapshot.batteryLevel <= SafetyConstants.BATTERY_LOW_THRESHOLD) {
            return WatchdogResult.Warning(
                reason = "Battery low (${snapshot.batteryLevel}%)"
            )
        }
        
        // WARNING: Ciclo de monitoreo muy largo
        if (timeSinceLastCycle > SafetyConstants.WATCHDOG_CHECK_INTERVAL * 2) {
            return WatchdogResult.Warning(
                reason = "Monitor cycle delayed (${timeSinceLastCycle / 1000}s)"
            )
        }
        
        return WatchdogResult.Healthy
    }
    
    /**
     * Obtiene el último timestamp de GPS conocido
     */
    fun getLastKnownGpsTimestamp(): Long {
        return currentSnapshot?.lastGpsUpdate ?: 0L
    }
    
    /**
     * Obtiene el último timestamp de ciclo de monitoreo
     */
    fun getLastKnownMonitorTimestamp(): Long {
        return currentSnapshot?.lastMonitorCycle ?: 0L
    }
    
    /**
     * Verifica si el sistema está saludable
     */
    fun isSystemHealthy(): Boolean {
        val snapshot = currentSnapshot ?: return false
        return evaluate(snapshot) is WatchdogResult.Healthy
    }
}
