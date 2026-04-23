package com.example.regresoacasa.domain.model

sealed class AppError {
    object NoInternet : AppError()
    object ApiDown : AppError()
    object LocationUnavailable : AppError()
    object PermissionDenied : AppError()
    data class Unknown(val message: String, val exception: Exception? = null) : AppError()
    data class RateLimited(val retryAfter: Long) : AppError()
    data class InvalidApiKey(val message: String = "API key inválida") : AppError()
    data class ServerError(val code: Int, val message: String) : AppError()
    data class Timeout(val message: String = "Tiempo de espera agotado") : AppError()
    
    fun toUserMessage(): String {
        return when (this) {
            NoInternet -> "Sin conexión a internet. Verifica tu red."
            ApiDown -> "El servicio no está disponible. Intenta más tarde."
            LocationUnavailable -> "No se puede obtener tu ubicación. Activa el GPS."
            PermissionDenied -> "Permiso denegado. Ve a Configuración para habilitarlo."
            is Unknown -> message
            is RateLimited -> "Demasiadas solicitudes. Espera ${retryAfter / 1000} segundos."
            is InvalidApiKey -> message
            is ServerError -> "Error del servidor ($code). $message"
            is Timeout -> message
        }
    }
    
    fun isRetryable(): Boolean {
        return when (this) {
            NoInternet, ApiDown, is RateLimited, is ServerError, is Timeout -> true
            LocationUnavailable, PermissionDenied, is InvalidApiKey, is Unknown -> false
        }
    }
}
