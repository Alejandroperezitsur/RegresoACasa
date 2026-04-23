package com.example.regresoacasa.ui.state

import com.example.regresoacasa.domain.model.AppError
import com.example.regresoacasa.domain.model.LocationState

sealed class HardenedUiState {
    object Loading : HardenedUiState()
    data class Success(val data: Any? = null) : HardenedUiState()
    data class Error(
        val error: AppError,
        val isRetryable: Boolean = error.isRetryable(),
        val userMessage: String = error.toUserMessage()
    ) : HardenedUiState()
    
    val isLoading: Boolean
        get() = this is Loading
    
    val isError: Boolean
        get() = this is Error
    
    val error: AppError?
        get() = (this as? Error)?.error
}

data class NetworkState(
    val isOnline: Boolean,
    val lastUpdate: Long = System.currentTimeMillis()
)

data class LocationTrackingState(
    val locationState: LocationState = LocationState.Searching,
    val lastUpdate: Long = System.currentTimeMillis(),
    val signalLostSince: Long? = null
) {
    val isSignalLost: Boolean
        get() = locationState == LocationState.Lost
    
    val signalLostDuration: Long?
        get() = signalLostSince?.let { System.currentTimeMillis() - it }
}
