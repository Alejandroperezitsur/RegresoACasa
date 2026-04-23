package com.example.regresoacasa.data.location

import com.example.regresoacasa.domain.model.UbicacionUsuario
import kotlin.math.abs

class RobustLocationFilter(
    private val minDistance: Double = 5.0, // 5 metros
    private val maxAccuracy: Float = 30f, // 30 metros
    private val windowSize: Int = 5
) {
    private val locationWindow = ArrayDeque<UbicacionUsuario>(windowSize)
    private var lastAcceptedLocation: UbicacionUsuario? = null
    
    fun filter(location: UbicacionUsuario): UbicacionUsuario? {
        // Check accuracy threshold
        if (location.precision != null && location.precision > maxAccuracy) {
            return null
        }
        
        // Check distance from last accepted location
        lastAcceptedLocation?.let { last ->
            val distance = haversineDistance(
                last.latitud, last.longitud,
                location.latitud, location.longitud
            )
            if (distance < minDistance) {
                return null // Too close, likely jitter
            }
        }
        
        // Add to window
        locationWindow.addLast(location)
        if (locationWindow.size > windowSize) {
            locationWindow.removeFirst()
        }
        
        // Calculate smoothed location (moving average)
        val smoothed = if (locationWindow.size >= 3) {
            val avgLat = locationWindow.map { it.latitud }.average()
            val avgLon = locationWindow.map { it.longitud }.average()
            val avgAccuracy = locationWindow.mapNotNull { it.precision }.average()
            location.copy(
                latitud = avgLat,
                longitud = avgLon,
                precision = avgAccuracy.toFloat()
            )
        } else {
            location
        }
        
        lastAcceptedLocation = smoothed
        return smoothed
    }
    
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }
    
    fun reset() {
        locationWindow.clear()
        lastAcceptedLocation = null
    }
}
