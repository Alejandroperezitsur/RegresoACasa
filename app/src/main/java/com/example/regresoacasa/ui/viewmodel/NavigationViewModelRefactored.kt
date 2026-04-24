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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NavigationViewModelRefactored(
    private val engine: SafeReturnEngine
) : ViewModel() {
    
    companion object {
        fun Factory(engine: SafeReturnEngine) = ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NavigationViewModelRefactored(engine) as T
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
    val connectionMessage: String? = null
)
