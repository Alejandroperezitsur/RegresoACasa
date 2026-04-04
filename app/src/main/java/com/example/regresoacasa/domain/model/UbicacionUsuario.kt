package com.example.regresoacasa.domain.model

/**
 * Modelo para ubicación del usuario
 */
data class UbicacionUsuario(
    val latitud: Double,
    val longitud: Double,
    val precision: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)
