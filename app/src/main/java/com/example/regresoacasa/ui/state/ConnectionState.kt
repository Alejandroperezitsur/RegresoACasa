package com.example.regresoacasa.ui.state

/**
 * Estados de conexión y sistema para manejo de estados críticos
 */
sealed class ConnectionState {
    data object Connected : ConnectionState()
    data object NoInternet : ConnectionState()
    data object GpsLost : ConnectionState()
    data class LowBattery(val level: Int) : ConnectionState()
    data class ApiError(val message: String) : ConnectionState()
}

/**
 * Información del estado de conexión con mensaje y acción sugerida
 */
data class CriticalStateInfo(
    val title: String,
    val message: String,
    val actionLabel: String?,
    val action: (() -> Unit)?,
    val isDismissible: Boolean = true,
    val color: CriticalStateColor = CriticalStateColor.WARNING
)

enum class CriticalStateColor {
    ERROR,      // Rojo - bloqueante
    WARNING,    // Amarillo - advertencia
    INFO        // Azul - informativo
}

/**
 * Mapea ConnectionState a información UI
 */
fun ConnectionState.toInfo(): CriticalStateInfo {
    return when (this) {
        is ConnectionState.Connected -> CriticalStateInfo(
            title = "",
            message = "",
            actionLabel = null,
            action = null
        )
        
        is ConnectionState.NoInternet -> CriticalStateInfo(
            title = "Sin conexión",
            message = "No se pueden recalcular rutas sin internet. Continuando con ruta guardada.",
            actionLabel = null,
            action = null,
            isDismissible = false,
            color = CriticalStateColor.WARNING
        )
        
        is ConnectionState.GpsLost -> CriticalStateInfo(
            title = "GPS no disponible",
            message = "Verifica que el GPS esté activado y los permisos concedidos.",
            actionLabel = "Configuración",
            action = { /* Ir a configuración */ },
            isDismissible = false,
            color = CriticalStateColor.ERROR
        )
        
        is ConnectionState.LowBattery -> CriticalStateInfo(
            title = "Batería baja",
            message = "Modo ahorro activado automáticamente.",
            actionLabel = "Entendido",
            action = { /* Dismiss */ },
            isDismissible = true,
            color = CriticalStateColor.WARNING
        )
        
        is ConnectionState.ApiError -> CriticalStateInfo(
            title = "Error de servicio",
            message = "No se pudo calcular la ruta. Reintentando automáticamente...",
            actionLabel = "Reintentar ahora",
            action = { /* Retry */ },
            isDismissible = true,
            color = CriticalStateColor.ERROR
        )
    }
}
