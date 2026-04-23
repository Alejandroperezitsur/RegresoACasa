package com.example.regresoacasa.domain.model

sealed class AppError {
    data class Network(val message: String, val exception: Exception? = null) : AppError()
    data class Permission(val permission: String, val isPermanent: Boolean = false) : AppError()
    data class GPS(val message: String, val isDisabled: Boolean = false) : AppError()
    data class API(val code: Int, val message: String) : AppError()
    data class Unknown(val message: String, val exception: Exception? = null) : AppError()
    
    fun toUserMessage(): String {
        return when (this) {
            is Network -> "Sin conexión a internet"
            is Permission -> if (isPermanent) "Permiso denegado permanentemente. Ve a Configuración" else "Permiso requerido"
            is GPS -> if (isDisabled) "GPS desactivado. Actívalo para continuar" else message
            is API -> when (code) {
                401 -> "Error de autenticación"
                429 -> "Demasiadas solicitudes. Espera un momento"
                in 500..599 -> "Error del servidor"
                else -> "Error de conexión"
            }
            is Unknown -> message
        }
    }
}
