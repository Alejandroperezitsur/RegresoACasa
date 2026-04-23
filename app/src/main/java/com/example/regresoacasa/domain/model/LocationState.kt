package com.example.regresoacasa.domain.model

sealed class LocationState {
    object Active : LocationState()
    object Disabled : LocationState()
    object Searching : LocationState()
    object Lost : LocationState()
    data class WeakSignal(val accuracy: Float) : LocationState()
    
    fun toUserMessage(): String {
        return when (this) {
            Active -> "GPS activo"
            Disabled -> "GPS desactivado"
            Searching -> "Buscando ubicación..."
            Lost -> "Señal GPS perdida"
            is WeakSignal -> "Señal GPS débil (~${accuracy.toInt()}m)"
        }
    }
}
