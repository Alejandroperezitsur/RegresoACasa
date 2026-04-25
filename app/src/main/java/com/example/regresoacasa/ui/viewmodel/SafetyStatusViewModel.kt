package com.example.regresoacasa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.regresoacasa.core.ConnectionStatus
import com.example.regresoacasa.core.GpsStatus
import com.example.regresoacasa.core.SafeReturnEngine
import com.example.regresoacasa.core.SafeReturnState
import com.example.regresoacasa.core.safety.state.SafetyMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SafetyStatusViewModel(
    private val engine: SafeReturnEngine
) : ViewModel() {
    
    companion object {
        fun Factory(engine: SafeReturnEngine): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SafetyStatusViewModel::class.java)) {
                        return SafetyStatusViewModel(engine) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
    
    private val _uiState = MutableStateFlow(SafetyStatusUiState())
    val uiState: StateFlow<SafetyStatusUiState> = _uiState.asStateFlow()
    
    private val _safetyMode = MutableStateFlow(SafetyMode.FULL)
    val safetyMode: StateFlow<SafetyMode> = _safetyMode.asStateFlow()
    
    private val _safetyScore = MutableStateFlow(100)
    val safetyScore: StateFlow<Int> = _safetyScore.asStateFlow()
    
    init {
        viewModelScope.launch {
            engine.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    isActive = state.isActive(),
                    isCritical = state.isCritical(),
                    isNavigating = state.isNavigating()
                )
            }
        }
        
        viewModelScope.launch {
            engine.gpsStatus.collect { status ->
                _uiState.value = _uiState.value.copy(
                    gpsMessage = when (status) {
                        is GpsStatus.Excellent -> null
                        is GpsStatus.Good -> null
                        is GpsStatus.Weak -> "Ubicación imprecisa"
                        is GpsStatus.Lost -> "Sin señal GPS"
                    },
                    gpsAccuracy = when (status) {
                        is GpsStatus.Weak -> status.accuracyMeters
                        else -> null
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
                        is ConnectionStatus.Degraded -> status.reason
                    }
                )
            }
        }
        
        // Safety mode and score will be updated by SafetyCore in production
        // For now, provide default values
        _safetyMode.value = SafetyMode.FULL
        _safetyScore.value = 100
    }
}

data class SafetyStatusUiState(
    val isActive: Boolean = false,
    val isCritical: Boolean = false,
    val isNavigating: Boolean = false,
    val gpsMessage: String? = null,
    val gpsAccuracy: Float? = null,
    val connectionMessage: String? = null
)
