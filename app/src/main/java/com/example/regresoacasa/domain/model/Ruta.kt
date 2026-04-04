package com.example.regresoacasa.domain.model

/**
 * Modelo de dominio para una ruta calculada
 */
data class Ruta(
    val distanciaMetros: Double,
    val duracionSegundos: Double,
    val puntos: List<PuntoRuta>,
    val instrucciones: List<Instruccion> = emptyList()
) {
    val distanciaFormateada: String
        get() = if (distanciaMetros >= 1000) {
            "%.2f km".format(distanciaMetros / 1000)
        } else {
            "%.0f m".format(distanciaMetros)
        }

    val duracionFormateada: String
        get() {
            val minutos = (duracionSegundos / 60).toInt()
            val horas = minutos / 60
            val minsRestantes = minutos % 60
            return if (horas > 0) {
                "$horas h ${minsRestantes} min"
            } else {
                "$minutos min"
            }
        }
}

data class PuntoRuta(
    val latitud: Double,
    val longitud: Double
)

data class Instruccion(
    val texto: String,
    val distancia: Double,
    val tipo: String
)
