package com.example.regresoacasa.domain.model

/**
 * Instrucción de navegación turn-by-turn
 */
data class InstruccionNavegacion(
    val texto: String,              // "Gira a la derecha en Calle Principal"
    val distancia: Double,          // Distancia en metros hasta la maniobra
    val tipo: TipoManiobra,         // Tipo de maniobra
    val nombreCalle: String = "",   // Nombre de la calle (si aplica)
    val completada: Boolean = false // Si ya se pasó esta instrucción
) {
    /**
     * Texto formateado para mostrar en UI
     */
    val textoCorto: String
        get() = when (tipo) {
            TipoManiobra.GIRA_DERECHA -> "Gira a la derecha"
            TipoManiobra.GIRA_IZQUIERDA -> "Gira a la izquierda"
            TipoManiobra.CONTINUA_RECTO -> "Continúa recto"
            TipoManiobra.MEDIA_VUELTA -> "Media vuelta"
            TipoManiobra.SALIDA -> "Toma la salida"
            TipoManiobra.ROTONDA -> "En la rotonda"
            TipoManiobra.DESTINO -> "Llegaste a tu destino"
        }
    
    val distanciaFormateada: String
        get() = if (distancia >= 1000) {
            "%.1f km".format(distancia / 1000)
        } else {
            "%.0f m".format(distancia)
        }
}

enum class TipoManiobra {
    GIRA_DERECHA,
    GIRA_IZQUIERDA,
    CONTINUA_RECTO,
    MEDIA_VUELTA,
    SALIDA,
    ROTONDA,
    DESTINO
}

/**
 * Estado completo de instrucciones de navegación
 */
data class NavigationInstructionsState(
    val instrucciones: List<InstruccionNavegacion> = emptyList(),
    val instruccionActual: InstruccionNavegacion? = null,
    val indiceActual: Int = 0,
    val distanciaProximaManiobra: Double = 0.0
)
