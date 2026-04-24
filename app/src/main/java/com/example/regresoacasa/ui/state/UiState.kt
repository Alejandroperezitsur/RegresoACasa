package com.example.regresoacasa.ui.state

import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.NavigationState
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario

/**
 * Estados UI sealed class para manejo robusto de estados
 */
sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val data: Any? = null) : UiState()
    data class Error(val message: String, val retryable: Boolean = true) : UiState()
}

/**
 * Estados específicos de navegación
 */
sealed class NavigationUiState {
    object Idle : NavigationUiState()
    object Calculating : NavigationUiState()
    data class Navigating(
        val navigationState: NavigationState
    ) : NavigationUiState()
    data class OffRoute(
        val lastKnownLocation: UbicacionUsuario,
        val recalculating: Boolean = false
    ) : NavigationUiState()
    data class Error(
        val message: String,
        val type: ErrorType
    ) : NavigationUiState()
}

enum class ErrorType {
    // Errores de red
    NO_INTERNET,
    TIMEOUT,
    
    // Errores de API
    API_ERROR,
    API_KEY_INVALID,
    SERVER_ERROR,
    RATE_LIMITED,
    
    // Errores de ubicación
    GPS_ERROR,
    GPS_LOST,
    LOCATION_PERMISSION_DENIED,
    GPS_DISABLED,
    
    // Errores de ruta
    NO_ROUTE,
    ROUTE_CALCULATION_FAILED,
    
    // Errores generales
    UNKNOWN,
    DATABASE_ERROR
}

/**
 * Estado completo de la UI principal
 */
data class MainUiState(
    val pantallaActual: Pantalla = Pantalla.MAP,
    val uiState: UiState = UiState.Idle,
    val navigationUiState: NavigationUiState = NavigationUiState.Idle,
    
    // Datos
    val ubicacionActual: UbicacionUsuario? = null,
    val casa: LugarFavorito? = null,
    val rutaActual: Ruta? = null,
    val navigationState: NavigationState = NavigationState(),
    
    // Búsqueda
    val busqueda: String = "",
    val resultadosBusqueda: List<Lugar> = emptyList(),
    val lugarSeleccionado: Lugar? = null,
    val mapCenterUbicacion: UbicacionUsuario? = null,
    val isSelectingOnMap: Boolean = false,
    
    // Flags
    val estaCargandoUbicacion: Boolean = false,
    val estaCalculandoRuta: Boolean = false,
    val estaBuscando: Boolean = false,
    val estaGuardando: Boolean = false,
    val isTrackingLocation: Boolean = false,
    
    // Nuevos: Estados de confianza y conexión (Hardening)
    val gpsAccuracy: Float? = null,
    val hasGpsSignal: Boolean = true,
    val connectionState: ConnectionState = ConnectionState.Connected,
    val batteryLevel: Int = 100,
    val isLowBatteryMode: Boolean = false,
    val safeReturnSession: com.example.regresoacasa.data.safety.SafeReturnSession? = null,
    val isSafeReturnActive: Boolean = false,
    val mapStyle: String = "Normal",
    
    // Estado de alerta de emergencia para feedback visual
    val emergencyAlertStatus: EmergencyAlertStatus = EmergencyAlertStatus.Idle
)

/**
 * Estado de alerta de emergencia para feedback visual al usuario
 */
sealed class EmergencyAlertStatus {
    object Idle : EmergencyAlertStatus()
    object Sending : EmergencyAlertStatus()
    data class Sent(val timestamp: Long, val phoneNumber: String) : EmergencyAlertStatus()
    data class Failed(val error: String, val timestamp: Long) : EmergencyAlertStatus()
}

enum class Pantalla {
    MAP,
    SEARCH,
    NAVEGACION,
    FAVORITES,
    SAFETY,
    EMERGENCY_CONTACTS
}
