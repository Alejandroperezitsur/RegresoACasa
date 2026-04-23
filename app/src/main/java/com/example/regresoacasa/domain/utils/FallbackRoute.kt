package com.example.regresoacasa.domain.utils

import com.example.regresoacasa.domain.model.PuntoRuta
import com.example.regresoacasa.domain.model.Ruta
import kotlin.math.*

/**
 * FASE 3: Fallback Route para sin internet
 * Calcula ruta simple en línea recta cuando falla ORS y no hay cache
 */
object FallbackRoute {
    
    /**
     * Genera una ruta simple en línea recta entre dos puntos
     * @param userLat Latitud del usuario
     * @param userLng Longitud del usuario
     * @param destLat Latitud del destino
     * @param destLng Longitud del destino
     * @return Ruta simple con distancia en línea recta
     */
    fun fallbackRoute(
        userLat: Double,
        userLng: Double,
        destLat: Double,
        destLng: Double
    ): Ruta {
        val distanceMeters = haversineDistance(userLat, userLng, destLat, destLng)
        
        // Estimar duración asumiendo caminata a 5 km/h (1.39 m/s)
        val walkingSpeed = 1.39 // m/s
        val durationSeconds = (distanceMeters / walkingSpeed).toDouble()
        
        // Generar puntos intermedios para visualización (cada 100m)
        val points = generateIntermediatePoints(userLat, userLng, destLat, destLng, distanceMeters)
        
        return Ruta(
            distanciaMetros = distanceMeters,
            duracionSegundos = durationSeconds,
            puntos = points,
            instrucciones = emptyList() // Sin instrucciones en fallback
        )
    }
    
    /**
     * Fórmula de Haversine para calcular distancia entre dos coordenadas
     */
    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371000.0 // Radio de la Tierra en metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
    
    /**
     * Genera puntos intermedios para visualización de ruta
     * @param interval Intervalo entre puntos en metros (default 100m)
     */
    private fun generateIntermediatePoints(
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
        totalDistance: Double,
        interval: Double = 100.0
    ): List<PuntoRuta> {
        val points = mutableListOf<PuntoRuta>()
        val numPoints = (totalDistance / interval).toInt().coerceAtMost(100) // Max 100 puntos
        
        for (i in 0..numPoints) {
            val fraction = i.toDouble() / numPoints
            val lat = startLat + (endLat - startLat) * fraction
            val lng = startLng + (endLng - startLng) * fraction
            points.add(PuntoRuta(lat, lng))
        }
        
        return points
    }
}
