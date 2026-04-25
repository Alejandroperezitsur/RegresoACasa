package com.example.regresoacasa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.regresoacasa.core.SafeReturnEngine
import com.example.regresoacasa.core.SafeReturnState
import com.example.regresoacasa.core.ConnectionStatus
import com.example.regresoacasa.core.GpsStatus
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.ui.state.Pantalla
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NavigationViewModel(
    private val engine: SafeReturnEngine
) : ViewModel() {
    
    companion object {
        fun Factory(engine: SafeReturnEngine): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(NavigationViewModel::class.java)) {
                        return NavigationViewModel(engine) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
    
    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            engine.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    safeReturnState = state,
                    isNavigating = state is SafeReturnState.Navigating,
                    isEmergency = state is SafeReturnState.Emergency,
                    isArrived = state is SafeReturnState.Arrived
                )
            }
        }
        
        viewModelScope.launch {
            engine.gpsStatus.collect { status ->
                _uiState.value = _uiState.value.copy(
                    gpsStatusMessage = when (status) {
                        is GpsStatus.Excellent -> null
                        is GpsStatus.Good -> null
                        is GpsStatus.Weak -> "Señal débil"
                        is GpsStatus.Lost -> "Sin señal GPS"
                    }
                )
            }
        }
        
        viewModelScope.launch {
            engine.connectionStatus.collect { status ->
                _uiState.value = _uiState.value.copy(
                    connectionMessage = when (status) {
                        is ConnectionStatus.Online -> null
                        is ConnectionStatus.Offline -> "Sin internet — usando SMS"
                        is ConnectionStatus.Degraded -> "Conexión inestable"
                    }
                )
            }
        }
    }
    
    fun startNavigation(destination: String, route: Ruta, startLocation: UbicacionUsuario) {
        engine.startNavigation(destination, route, startLocation)
    }
    
    fun stopNavigation() {
        engine.stopNavigation()
    }
    
    fun markAsArrived() {
        engine.markAsArrived()
    }
    
    // Methods needed by MainActivity - placeholders for now
    fun obtenerUbicacionUnica() {
        // TODO: Implement single location fetch
    }
    
 fun cargarCasa() {
        // TODO: Load home location from database
    }
    
    fun onSearchQueryChange(query: String) {
        // TODO: Handle search query
    }
    
    fun cambiarPantalla(pantalla: Pantalla) {
        _uiState.value = _uiState.value.copy(pantallaActual = pantalla)
    }
    
    fun guardarCasaDesdeLugar(lugar: Lugar) {
        // TODO: Save as home location
    }
    
    fun iniciarNavegacionConDestino(lugar: Lugar) {
        // TODO: Start navigation to destination
    }
    
    fun iniciarNavegacion() {
        // TODO: Start navigation to home
    }
    
    fun toggleFollowUser() {
        _uiState.value = _uiState.value.copy(
            navigationState = _uiState.value.navigationState.copy(
                isFollowingUser = !_uiState.value.navigationState.isFollowingUser
            )
        )
    }
    
    fun onMapMove(lat: Double, lon: Double) {
        _uiState.value = _uiState.value.copy(
            mapCenterUbicacion = UbicacionUsuario(lat, lon, null, System.currentTimeMillis())
        )
    }
    
    fun onMapLongClick(lat: Double, lon: Double) {
        _uiState.value = _uiState.value.copy(
            isSelectingOnMap = true,
            mapCenterUbicacion = UbicacionUsuario(lat, lon, null, System.currentTimeMillis())
        )
    }
    
    fun confirmarSeleccionMapa() {
        _uiState.value = _uiState.value.copy(
            isSelectingOnMap = false
        )
    }
    
    fun cancelarSeleccionMapa() {
        _uiState.value = _uiState.value.copy(
            isSelectingOnMap = false,
            mapCenterUbicacion = null
        )
    }
    
    fun eliminarCasa() {
        _uiState.value = _uiState.value.copy(casa = null)
    }
    
    fun toggleGuardian(phoneNumber: String) {
        // TODO: Implement guardian toggle
    }
    
    fun sendEmergencyAlert() {
        engine.triggerEmergency("Alerta desde navegación")
    }
    
    fun stopSafeTrip() {
        engine.stopNavigation()
    }
    
    fun triggerEmergency() {
        engine.triggerEmergency("Alerta manual")
    }
    
    fun limpiarError() {
        _uiState.value = _uiState.value.copy(
            uiState = com.example.regresoacasa.ui.state.UiState.Idle
        )
    }
    
    // Additional methods needed by SearchScreen
    fun iniciarSeleccionMapa() {
        _uiState.value = _uiState.value.copy(isSelectingOnMap = true)
    }
    
    fun seleccionarLugar(lugar: Lugar) {
        _uiState.value = _uiState.value.copy(lugarSeleccionado = lugar)
    }
    
    // Additional methods needed by NavigationScreen
    fun onShare() {
        // TODO: Implement share functionality
    }
    
    override fun onCleared() {
        super.onCleared()
        engine.cleanup()
    }
}

data class NavigationUiState(
    val safeReturnState: SafeReturnState = SafeReturnState.Idle,
    val isNavigating: Boolean = false,
    val isEmergency: Boolean = false,
    val isArrived: Boolean = false,
    val gpsStatusMessage: String? = null,
    val connectionMessage: String? = null,
    val pantallaActual: Pantalla = Pantalla.MAP,
    val ubicacionActual: UbicacionUsuario? = null,
    val casa: LugarFavorito? = null,
    val rutaActual: Ruta? = null,
    val lugarSeleccionado: Lugar? = null,
    val isSelectingOnMap: Boolean = false,
    val mapCenterUbicacion: UbicacionUsuario? = null,
    val mapStyle: String = "normal",
    val navigationState: NavigationState = NavigationState(),
    val isSafeReturnActive: Boolean = false,
    val estaCargandoUbicacion: Boolean = false,
    val estaCalculandoRuta: Boolean = false,
    val uiState: com.example.regresoacasa.ui.state.UiState = com.example.regresoacasa.ui.state.UiState.Idle,
    // Properties needed by SearchScreen
    val busqueda: String = "",
    val estaBuscando: Boolean = false,
    val resultadosBusqueda: List<Lugar> = emptyList(),
    val estaGuardando: Boolean = false,
    // Properties needed by NavigationScreen
    val connectionState: ConnectionState = ConnectionState.Online,
    val systemFeedbackState: SystemFeedbackState = SystemFeedbackState.Idle,
    val remainingDuration: Long = 0L
)

data class NavigationState(
    val isFollowingUser: Boolean = true
)

enum class ConnectionState {
    Online,
    Offline,
    Degraded
}

enum class SystemFeedbackState {
    Idle,
    Success,
    Error,
    Warning
}
