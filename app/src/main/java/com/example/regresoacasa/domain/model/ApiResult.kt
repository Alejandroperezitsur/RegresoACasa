package com.example.regresoacasa.domain.model

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val error: ApiError) : ApiResult<Nothing>()
}

sealed class ApiError(val message: String, val code: Int = -1) {
    // Errores de autenticación
    data object InvalidApiKey : ApiError("API key inválida. Verifica tu configuración.", 401)
    
    // Errores de rate limiting
    data class RateLimited(val retryAfter: Long = 1000) : ApiError("Demasiadas solicitudes. Reintentando...", 429)
    
    // Errores de red
    data object NoInternet : ApiError("Sin conexión a internet", -1)
    data object Timeout : ApiError("Tiempo de espera agotado. Intenta de nuevo.", -1)
    
    // Errores de servidor
    data object ServerError : ApiError("Error del servidor. Intenta más tarde.", 500)
    data object ServiceUnavailable : ApiError("Servicio no disponible temporalmente.", 503)
    
    // Errores de cliente/validación
    data object BadRequest : ApiError("Solicitud inválida. Verifica los datos.", 400)
    data object NotFound : ApiError("No se encontró la ruta solicitada.", 404)
    
    // Errores de parsing/datos
    data object InvalidResponse : ApiError("Respuesta inválida del servidor.", -1)
    data object EmptyResponse : ApiError("No hay datos disponibles.", -1)
    
    // Errores de permisos
    data object LocationPermissionDenied : ApiError("Permiso de ubicación requerido.", -1)
    
    // Errores genéricos
    data class Unknown(val exception: Throwable? = null) : ApiError(
        exception?.message ?: "Error desconocido",
        -1
    )
}

// Extension functions para facilitar el uso
fun <T> ApiResult<T>.getOrNull(): T? = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Error -> null
}

fun <T> ApiResult<T>.getOrElse(defaultValue: T): T = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Error -> defaultValue
}

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(data)
    return this
}

inline fun <T> ApiResult<T>.onError(action: (ApiError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) action(error)
    return this
}

// Convertir retrofit Response a ApiResult
fun <T> retrofit2.Response<T>.toApiResult(): ApiResult<T> {
    return when {
        isSuccessful -> {
            body()?.let { ApiResult.Success(it) }
                ?: ApiResult.Error(ApiError.EmptyResponse)
        }
        code() == 401 -> ApiResult.Error(ApiError.InvalidApiKey)
        code() == 429 -> {
            val retryAfter = headers()["Retry-After"]?.toLongOrNull()?.times(1000) ?: 1000
            ApiResult.Error(ApiError.RateLimited(retryAfter))
        }
        code() == 400 -> ApiResult.Error(ApiError.BadRequest)
        code() == 404 -> ApiResult.Error(ApiError.NotFound)
        code() in 500..599 -> ApiResult.Error(ApiError.ServerError)
        else -> ApiResult.Error(ApiError.Unknown(
            Exception("HTTP ${code()}: ${errorBody()?.string()}")
        ))
    }
}
