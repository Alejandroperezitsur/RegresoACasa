package com.example.regresoacasa.domain.model

/**
 * Modelo de dominio para un lugar genérico (resultado de búsqueda)
 */
data class Lugar(
    val id: String,
    val nombre: String,
    val direccion: String,
    val latitud: Double,
    val longitud: Double
)
