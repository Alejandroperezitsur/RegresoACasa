package com.example.regresoacasa.core.safety

import android.content.Context
import android.util.Log
import com.example.regresoacasa.core.safety.alert.AlertEngine
import com.example.regresoacasa.core.safety.alert.LocationData
import com.example.regresoacasa.core.safety.heartbeat.HeartbeatManager
import com.example.regresoacasa.core.safety.location.LocationEngine
import com.example.regresoacasa.core.safety.location.LocationUpdate
import com.example.regresoacasa.core.safety.location.SpoofingDetector
import com.example.regresoacasa.core.safety.persistence.SafetyPersistence
import com.example.regresoacasa.core.safety.power.EnergyManager
import com.example.regresoacasa.core.safety.security.AntiKillDetector
import com.example.regresoacasa.core.safety.security.IntegrityGuard
import com.example.regresoacasa.core.safety.security.RateLimiter
import com.example.regresoacasa.core.safety.state.LocationSnapshot
import com.example.regresoacasa.core.safety.state.SafetyMode
import com.example.regresoacasa.core.safety.state.SafetySnapshot
import com.example.regresoacasa.core.safety.state.SafetyState
import com.example.regresoacasa.core.safety.watchdog.SafetyWatchdog
import com.example.regresoacasa.core.safety.watchdog.WorkManagerWatchdog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Safety Core - Orquestador Central del Sistema
 * 
 * Responsabilidades:
 * - Orquestar TODO el sistema de seguridad
 * - Mantener estado global
 * - Cambiar modos de operación
 * - Coordinar alertas
 * - Sobrevivir a process death
 * 
 * PRINCIPIO: Safety > UX > Performance
 * El sistema SIEMPRE funciona, incluso sin UI.
 */
class SafetyCore(
    private val context: Context,
    private val locationEngine: LocationEngine,
    private val alertEngine: AlertEngine,
    private val watchdog: SafetyWatchdog,
    private val persistence: SafetyPersistence
) {
    
    // V3 Componentes de seguridad avanzada
    private val integrityGuard = IntegrityGuard(context)
    private val heartbeatManager = HeartbeatManager(context, scope)
    private val antiKillDetector = AntiKillDetector(context, scope)
    private val spoofingDetector = SpoofingDetector()
    private val energyManager = EnergyManager(context, scope)
    private val safetyScoreCalculator = SafetyScoreCalculator()
    private val rateLimiter = RateLimiter()
    
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    
    // Estado global del sistema
    private val _state = MutableStateFlow<SafetyState>(SafetyState.Idle)
    val state: StateFlow<SafetyState> = _state.asStateFlow()
    
    // Modo actual de operación
    private val _mode = MutableStateFlow<SafetyMode>(SafetyMode.FULL)
    val mode: StateFlow<SafetyMode> = _mode.asStateFlow()
    
    // Última ubicación conocida
    private val _lastLocation = MutableStateFlow<LocationSnapshot?>(null)
    val lastLocation: StateFlow<LocationSnapshot?> = _lastLocation.asStateFlow()
    
    // Estado de conexión
    private val _hasInternet = MutableStateFlow(true)
    val hasInternet: StateFlow<Boolean> = _hasInternet.asStateFlow()
    
    // Nivel de batería
    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()
    
    // Tracking jobs
    private var locationJob: Job? = null
    private var snapshotJob: Job? = null
    
    // Timestamps para watchdog
    private var lastGpsUpdate: Long = 0L
    private var lastMonitorCycle: Long = 0L
    
    init {
        // Configurar callbacks del watchdog
        watchdog.onCriticalAlert = { reason ->
            handleCriticalAlert(reason)
        }
        
        // V3: Configurar callbacks de componentes avanzados
        heartbeatManager.onSystemDead = { timeSinceLastBeat ->
            handleSystemDead(timeSinceLastBeat)
        }
        
        antiKillDetector.onUnexpectedKill = { runtime ->
            handleUnexpectedKill(runtime)
        }
        
        // Iniciar componentes V3
        energyManager.startMonitoring()
        heartbeatManager.start()
        WorkManagerWatchdog.schedule(context)
        
        // Marcar app como running
        antiKillDetector.markRunning()
        
        // Verificar integrity al iniciar
        checkIntegrity()
        
        // Verificar kill inesperado al iniciar
        antiKillDetector.checkUnexpectedKill()
        
        // Iniciar loop de guardado de snapshots
        startSnapshotLoop()
        
        Log.d("SafetyCore", "SafetyCore initialized with V3 components")
    }
    
    /**
     * Inicia el monitoreo de seguridad
     */
    fun startMonitoring() {
        if (_state.value.isActive()) {
            Log.w("SafetyCore", "Already monitoring")
            return
        }
        
        _state.value = SafetyState.Monitoring
        watchdog.start()
        alertEngine.registerReceivers()
        
        // Iniciar tracking de ubicación
        startLocationTracking()
        
        // Evaluar modo inicial
        evaluateMode()
        
        Log.d("SafetyCore", "Safety monitoring started")
    }
    
    /**
     * Inicia navegación a un destino
     */
    fun startNavigation(destination: String) {
        _state.value = SafetyState.Navigating(
            destination = destination,
            startTime = System.currentTimeMillis()
        )
        
        watchdog.start()
        alertEngine.registerReceivers()
        startLocationTracking()
        evaluateMode()
        
        Log.d("SafetyCore", "Navigation started to $destination")
    }
    
    /**
     * Detiene el monitoreo/navegación
     */
    fun stop() {
        locationJob?.cancel()
        locationEngine.stop()
        watchdog.stop()
        alertEngine.unregisterReceivers()
        
        // V3: Marcar shutdown limpio
        antiKillDetector.markCleanShutdown()
        heartbeatManager.stop()
        
        _state.value = SafetyState.Idle
        _mode.value = SafetyMode.FULL
        
        // Limpiar snapshot
        scope.launch {
            persistence.clearSnapshot()
        }
        
        Log.d("SafetyCore", "Safety monitoring stopped")
    }
    
    /**
     * Fuerza una alerta de emergencia manual
     * 
     * CRÍTICO: Esta función SIEMPRE funciona, sin condiciones.
     */
    fun forceEmergency(reason: String = "Manual trigger") {
        // V3: Verificar rate limiting
        if (!rateLimiter.isAllowed("alert")) {
            Log.w("SafetyCore", "Alert rate limited")
            return
        }
        
        scope.launch {
            _state.value = SafetyState.AlertTriggered(
                reason = reason,
                timestamp = System.currentTimeMillis()
            )
            
            val location = _lastLocation.value?.let {
                LocationData(it.latitude, it.longitude, it.accuracy)
            }
            
            val result = alertEngine.triggerEmergency(
                reason = reason,
                location = location,
                batteryLevel = _batteryLevel.value
            )
            
            Log.d("SafetyCore", "Emergency triggered: $reason, result: $result")
        }
    }
    
    /**
     * Restaura el sistema desde un snapshot
     * 
     * CRÍTICO: Permite recuperación después de process death.
     */
    suspend fun restore(snapshot: SafetySnapshot) {
        Log.d("SafetyCore", "Restoring from snapshot: ${snapshot.state}")
        
        _state.value = snapshot.state
        _mode.value = snapshot.mode
        _lastLocation.value = snapshot.lastLocation
        _batteryLevel.value = snapshot.batteryLevel
        _hasInternet.value = snapshot.hasInternet
        
        // Reaniciar componentes según estado
        when (snapshot.state) {
            is SafetyState.Idle -> {
                // No hacer nada
            }
            is SafetyState.Monitoring -> {
                startMonitoring()
            }
            is SafetyState.Navigating -> {
                val navState = snapshot.state as SafetyState.Navigating
                startNavigation(navState.destination)
            }
            is SafetyState.AlertTriggered -> {
                // Reintentar alertas pendientes
                alertEngine.retryPendingAlerts()
                watchdog.start()
                alertEngine.registerReceivers()
            }
            is SafetyState.Critical -> {
                // Modo crítico, reanimar monitoreo
                watchdog.start()
                alertEngine.registerReceivers()
                startLocationTracking()
            }
        }
        
        Log.d("SafetyCore", "Restore completed")
    }
    
    /**
     * Actualiza el estado de internet
     */
    fun updateInternetStatus(hasConnection: Boolean) {
        _hasInternet.value = hasConnection
        evaluateMode()
    }
    
    /**
     * Actualiza el nivel de batería
     */
    fun updateBatteryLevel(level: Int) {
        _batteryLevel.value = level
        evaluateMode()
    }
    
    /**
     * V3: Obtiene el score de seguridad actual
     */
    fun getSafetyScore(): SafetyScoreCalculator.SafetyScore {
        val timeSinceLastHeartbeat = scope.launch {
            heartbeatManager.getTimeSinceLastHeartbeat()
        }
        
        return safetyScoreCalculator.calculate(
            mode = _mode.value,
            gpsAccuracy = _lastLocation.value?.accuracy,
            hasInternet = _hasInternet.value,
            batteryLevel = _batteryLevel.value,
            isCharging = energyManager.isCharging.value,
            timeSinceLastHeartbeat = 0L // TODO: obtener real
        )
    }
    
    /**
     * V3: Verifica integridad del dispositivo
     */
    private fun checkIntegrity() {
        val result = integrityGuard.checkIntegrity()
        
        if (result.isCompromised) {
            Log.e("SafetyCore", "Device compromised: ${result.reasons}")
            
            if (result.severity == IntegrityGuard.IntegrityResult.Severity.CRITICAL) {
                // Entrar en modo crítico
                _mode.value = SafetyMode.CRITICAL
                handleCriticalAlert("Device compromised")
            }
        }
    }
    
    /**
     * V3: Maneja sistema muerto (heartbeat timeout)
     */
    private fun handleSystemDead(timeSinceLastBeat: Long) {
        Log.e("SafetyCore", "System dead for ${timeSinceLastBeat}ms")
        handleCriticalAlert("System unresponsive")
    }
    
    /**
     * V3: Maneja kill inesperado
     */
    private fun handleUnexpectedKill(runtime: Long) {
        Log.e("SafetyCore", "Unexpected kill detected, runtime: ${runtime}ms")
        handleCriticalAlert("App terminated unexpectedly")
    }
    
    /**
     * Inicia el tracking de ubicación
     */
    private fun startLocationTracking() {
        locationJob?.cancel()
        
        locationJob = scope.launch {
            locationEngine.start().collect { update ->
                when (update) {
                    is LocationUpdate.Reliable -> {
                        lastGpsUpdate = System.currentTimeMillis()
                        
                        _lastLocation.value = LocationSnapshot(
                            latitude = update.location.latitude,
                            longitude = update.location.longitude,
                            accuracy = update.accuracy,
                            timestamp = update.location.time,
                            isReliable = true
                        )
                        
                        // Actualizar watchdog
                        updateWatchdogSnapshot()
                    }
                    is LocationUpdate.Degraded -> {
                        _lastLocation.value = LocationSnapshot(
                            latitude = update.lastKnownLocation.latitude,
                            longitude = update.lastKnownLocation.longitude,
                            accuracy = update.lastKnownLocation.accuracy,
                            timestamp = update.lastKnownLocation.time,
                            isReliable = false
                        )
                        
                        evaluateMode()
                    }
                    is LocationUpdate.Error -> {
                        Log.e("SafetyCore", "Location error: ${update.error}")
                        evaluateMode()
                    }
                }
            }
        }
    }
    
    /**
     * Evalúa el modo de operación actual
     * 
     * CRÍTICO: Cambia comportamiento según condiciones.
     */
    private fun evaluateMode() {
        val newMode = when {
            !_hasInternet.value && _lastLocation.value == null -> SafetyMode.SMS_ONLY
            !_hasInternet.value -> SafetyMode.NO_INTERNET
            _lastLocation.value?.isReliable == false -> SafetyMode.LOW_GPS
            _batteryLevel.value <= SafetyConstants.BATTERY_CRITICAL_THRESHOLD -> SafetyMode.CRITICAL
            else -> SafetyMode.FULL
        }
        
        if (newMode != _mode.value) {
            val oldMode = _mode.value
            _mode.value = newMode
            Log.d("SafetyCore", "Mode changed: $oldMode -> $newMode")
            
            // Notificar al watchdog del cambio de modo
            watchdog.onModeChange?.invoke(newMode)
        }
    }
    
    /**
     * Actualiza el snapshot del watchdog
     */
    private fun updateWatchdogSnapshot() {
        lastMonitorCycle = System.currentTimeMillis()
        
        watchdog.updateSnapshot(
            SafetyWatchdog.SafetySnapshot(
                lastGpsUpdate = lastGpsUpdate,
                lastMonitorCycle = lastMonitorCycle,
                hasGpsSignal = _lastLocation.value?.isReliable == true,
                gpsAccuracy = _lastLocation.value?.accuracy,
                batteryLevel = _batteryLevel.value,
                isNavigating = _state.value.isNavigating()
            )
        )
    }
    
    /**
     * Maneja una alerta crítica del watchdog
     */
    private fun handleCriticalAlert(reason: String) {
        scope.launch {
            _state.value = SafetyState.Critical(
                reason = reason,
                timestamp = System.currentTimeMillis()
            )
            
            // Enviar alerta automática
            val location = _lastLocation.value?.let {
                LocationData(it.latitude, it.longitude, it.accuracy)
            }
            
            alertEngine.triggerEmergency(
                reason = "AUTO: $reason",
                location = location,
                batteryLevel = _batteryLevel.value
            )
            
            Log.e("SafetyCore", "Critical alert handled: $reason")
        }
    }
    
    /**
     * Inicia el loop de guardado de snapshots
     */
    private fun startSnapshotLoop() {
        snapshotJob = scope.launch {
            while (true) {
                delay(SafetyConstants.SNAPSHOT_SAVE_INTERVAL)
                saveSnapshot()
            }
        }
    }
    
    /**
     * Guarda el snapshot actual
     */
    private suspend fun saveSnapshot() {
        val snapshot = SafetySnapshot(
            state = _state.value,
            mode = _mode.value,
            lastLocation = _lastLocation.value,
            lastUpdate = System.currentTimeMillis(),
            batteryLevel = _batteryLevel.value,
            hasInternet = _hasInternet.value
        )
        
        persistence.saveSnapshot(snapshot)
    }
    
    /**
     * Limpia recursos
     */
    fun cleanup() {
        locationJob?.cancel()
        snapshotJob?.cancel()
        locationEngine.stop()
        watchdog.stop()
        alertEngine.unregisterReceivers()
        
        // V3: Limpiar componentes avanzados
        heartbeatManager.stop()
        WorkManagerWatchdog.cancel(context)
        
        scope.cancel()
        
        Log.d("SafetyCore", "SafetyCore cleaned up")
    }
}
