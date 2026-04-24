package com.example.regresoacasa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.regresoacasa.RegresoACasaApp
import com.example.regresoacasa.core.safety.SafetyCore
import com.example.regresoacasa.core.safety.state.SafetyMode
import com.example.regresoacasa.core.safety.state.SafetyState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Safety ViewModel - UI desconectada del Core
 * 
 * Este ViewModel SOLO observa el estado del SafetyCore.
 * NO controla lógica crítica - eso es responsabilidad del SafetyCore.
 * 
 * PRINCIPIO: UI es opcional, el sistema no.
 */
class SafetyViewModel : ViewModel() {
    
    private val safetyCore: SafetyCore
        get() = RegresoACasaApp.getInstance().safetyCore
    
    // Observar estado del SafetyCore
    val safetyState: StateFlow<SafetyState>
        get() = safetyCore.state
    
    val safetyMode: StateFlow<SafetyMode>
        get() = safetyCore.mode
    
    val lastLocation: StateFlow<com.example.regresoacasa.core.safety.state.LocationSnapshot?>
        get() = safetyCore.lastLocation
    
    val hasInternet: StateFlow<Boolean>
        get() = safetyCore.hasInternet
    
    val batteryLevel: StateFlow<Int>
        get() = safetyCore.batteryLevel
    
    init {
        // El SafetyCore ya está inicializado en Application
        // Este ViewModel solo observa su estado
    }
    
    /**
     * Inicia el monitoreo de seguridad
     * Delega al SafetyCore
     */
    fun startMonitoring() {
        safetyCore.startMonitoring()
    }
    
    /**
     * Inicia navegación a un destino
     * Delega al SafetyCore
     */
    fun startNavigation(destination: String) {
        safetyCore.startNavigation(destination)
    }
    
    /**
     * Detiene el monitoreo/navegación
     * Delega al SafetyCore
     */
    fun stop() {
        safetyCore.stop()
    }
    
    /**
     * Fuerza una alerta de emergencia manual
     * Delega al SafetyCore
     * 
     * CRÍTICO: Esta función SIEMPRE funciona.
     */
    fun forceEmergency(reason: String = "Manual trigger") {
        safetyCore.forceEmergency(reason)
    }
    
    /**
     * Actualiza el estado de internet
     * Delega al SafetyCore
     */
    fun updateInternetStatus(hasConnection: Boolean) {
        safetyCore.updateInternetStatus(hasConnection)
    }
    
    /**
     * Actualiza el nivel de batería
     * Delega al SafetyCore
     */
    fun updateBatteryLevel(level: Int) {
        safetyCore.updateBatteryLevel(level)
    }
    
    /**
     * Envía alerta de emergencia (compatibilidad con código existente)
     * Esta función es un wrapper para forceEmergency
     */
    fun sendEmergencyAlert() {
        forceEmergency("Manual trigger from UI")
    }
    
    override fun onCleared() {
        super.onCleared()
        // NO detener el SafetyCore - sigue funcionando sin UI
        // El SafetyCore se limpia solo cuando la app se destruye completamente
    }
}
