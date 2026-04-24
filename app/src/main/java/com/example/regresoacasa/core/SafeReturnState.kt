package com.example.regresoacasa.core

import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.model.Ruta

sealed class SafeReturnState {
    object Idle : SafeReturnState()
    
    object Preparing : SafeReturnState()
    
    data class Navigating(
        val destination: String,
        val route: Ruta,
        val currentLocation: UbicacionUsuario,
        val startTime: Long,
        val remainingDistance: Double,
        val eta: String
    ) : SafeReturnState()
    
    data class Emergency(
        val reason: String,
        val timestamp: Long,
        val deliveryStatus: EmergencyDeliveryStatus,
        val lastLocation: UbicacionUsuario?
    ) : SafeReturnState()
    
    data class Arrived(
        val destination: String,
        val duration: Long,
        val distance: Double
    ) : SafeReturnState()
    
    data class Failure(
        val reason: String,
        val isRecoverable: Boolean,
        val timestamp: Long
    ) : SafeReturnState()
    
    fun isActive(): Boolean = this !is Idle
    fun isCritical(): Boolean = this is Emergency || this is Failure
    fun isNavigating(): Boolean = this is Navigating
}

sealed class EmergencyDeliveryStatus {
    object Sending : EmergencyDeliveryStatus()
    data class DeliveredInternet(val timestamp: Long) : EmergencyDeliveryStatus()
    data class DeliveredSMS(val timestamp: Long) : EmergencyDeliveryStatus()
    data class FailedRetrying(val attempt: Int, val maxAttempts: Int) : EmergencyDeliveryStatus()
    object PermanentlyFailed : EmergencyDeliveryStatus()
}

sealed class ConnectionStatus {
    object Online : ConnectionStatus()
    object Offline : ConnectionStatus()
    data class Degraded(val reason: String) : ConnectionStatus()
}

sealed class GpsStatus {
    object Excellent : GpsStatus()
    object Good : GpsStatus()
    data class Weak(val accuracyMeters: Float) : GpsStatus()
    object Lost : GpsStatus()
}
