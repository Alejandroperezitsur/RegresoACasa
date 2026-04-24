package com.example.regresoacasa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.regresoacasa.core.EmergencyDeliveryStatus
import com.example.regresoacasa.core.SafeReturnEngine
import com.example.regresoacasa.core.SafeReturnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmergencyViewModel(
    private val engine: SafeReturnEngine
) : ViewModel() {
    
    companion object {
        fun Factory(engine: SafeReturnEngine) = ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EmergencyViewModel(engine) as T
            }
        }
    }
    
    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            engine.state.collect { state ->
                if (state is SafeReturnState.Emergency) {
                    _uiState.value = _uiState.value.copy(
                        isActive = true,
                        deliveryStatus = state.deliveryStatus,
                        deliveryMessage = getDeliveryMessage(state.deliveryStatus),
                        timestamp = state.timestamp
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isActive = false,
                        deliveryStatus = null,
                        deliveryMessage = null,
                        timestamp = null
                    )
                }
            }
        }
    }
    
    fun triggerEmergency(reason: String) {
        engine.triggerEmergency(reason)
    }
    
    private fun getDeliveryMessage(status: EmergencyDeliveryStatus): String {
        return when (status) {
            is EmergencyDeliveryStatus.Sending -> "Enviando..."
            is EmergencyDeliveryStatus.DeliveredInternet -> "✔ Alerta enviada por internet"
            is EmergencyDeliveryStatus.DeliveredSMS -> "✔ Alerta enviada por SMS"
            is EmergencyDeliveryStatus.FailedRetrying -> "⚠ Reintentando... (${status.attempt}/${status.maxAttempts})"
            is EmergencyDeliveryStatus.PermanentlyFailed -> "❌ No se pudo enviar la alerta"
        }
    }
}

data class EmergencyUiState(
    val isActive: Boolean = false,
    val deliveryStatus: EmergencyDeliveryStatus? = null,
    val deliveryMessage: String? = null,
    val timestamp: Long? = null
)
