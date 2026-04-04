package com.example.regresoacasa.domain.model

/**
 * Modelo de dominio para lugar favorito guardado
 */
data class LugarFavorito(
    val id: String,
    val nombre: String, // "Casa", "Trabajo", "Gimnasio", etc.
    val direccion: String,
    val latitud: Double,
    val longitud: Double,
    val tipo: TipoFavorito = TipoFavorito.OTRO
) {
    enum class TipoFavorito {
        CASA, TRABAJO, OTRO
    }
}
