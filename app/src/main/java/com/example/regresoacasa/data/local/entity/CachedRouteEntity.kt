package com.example.regresoacasa.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad para caché de rutas en base de datos local
 * Permite funcionamiento offline parcial
 */
@Entity(tableName = "cached_routes")
data class CachedRouteEntity(
    @PrimaryKey val key: String,
    val data: String, // JSON completo de la ruta
    val timestamp: Long,
    val profile: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double
) {
    companion object {
        fun generateKey(profile: String, startLat: Double, startLng: Double, endLat: Double, endLng: Double): String {
            return "${profile}:${startLat},$startLng:${endLat},$endLng"
        }
    }
}
