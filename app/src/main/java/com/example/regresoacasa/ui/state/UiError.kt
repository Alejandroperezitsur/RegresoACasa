package com.example.regresoacasa.ui.state

/**
 * FASE 6: Errores visibles - NO logs silenciosos
 * Todos los errores deben mostrarse al usuario
 */
sealed class UiError {
    object NoInternet : UiError()
    object ApiKeyInvalid : UiError()
    object RouteFailed : UiError()
    object GpsDisabled : UiError()
    object LocationPermissionDenied : UiError()
    data class ApiError(val message: String) : UiError()
    data class Unknown(val message: String) : UiError()
    
    /**
     * Mensaje amigable para el usuario
     */
    fun getUserMessage(): String {
        return when (this) {
            NoInternet -> "Sin conexión a internet. Verifica tu red."
            ApiKeyInvalid -> "Error de configuración. La API key no es válida."
            RouteFailed -> "No se pudo calcular la ruta. Intenta de nuevo."
            GpsDisabled -> "El GPS está desactivado. Actívalo para continuar."
            LocationPermissionDenied -> "Permiso de ubicación denegado. Ve a Configuración."
            is ApiError -> "Error del servidor: ${message}"
            is Unknown -> "Error inesperado: $message"
        }
    }
    
    /**
     * Indica si el error es recuperable (el usuario puede hacer algo)
     */
    fun isRecoverable(): Boolean {
        return when (this) {
            NoInternet, GpsDisabled, LocationPermissionDenied -> true
            else -> false
        }
    }
}
