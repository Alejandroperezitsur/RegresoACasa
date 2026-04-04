package com.example.regresoacasa.domain.utils

import com.example.regresoacasa.domain.model.PuntoRuta
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utilidad para ajustar la ubicación del usuario a la ruta más cercana (snap-to-route)
 */
object SnapToRoute {
    
    private const val EARTH_RADIUS = 6371000.0 // metros
    
    /**
     * Ajusta la ubicación del usuario a la polilínea de la ruta más cercana
     * @param ubicacion Ubicación real del GPS
     * @param ruta Ruta calculada con puntos
     * @param maxDistance Distancia máxima para hacer snap (metros)
     * @return Ubicación ajustada o la original si está muy lejos
     */
    fun snap(
        ubicacion: UbicacionUsuario, 
        ruta: Ruta, 
        maxDistance: Double = 30.0
    ): UbicacionUsuario {
        if (ruta.puntos.isEmpty()) return ubicacion
        
        // Encontrar el punto más cercano en la polilínea
        var minDistance = Double.MAX_VALUE
        var closestPoint = ubicacion
        
        // Revisar cada segmento de la ruta
        for (i in 0 until ruta.puntos.size - 1) {
            val start = ruta.puntos[i]
            val end = ruta.puntos[i + 1]
            
            val projected = projectPointToSegment(ubicacion, start, end)
            val distance = haversineDistance(
                ubicacion.latitud, ubicacion.longitud,
                projected.latitud, projected.longitud
            )
            
            if (distance < minDistance) {
                minDistance = distance
                closestPoint = projected
            }
        }
        
        // Solo hacer snap si está dentro del umbral
        return if (minDistance <= maxDistance) {
            UbicacionUsuario(
                latitud = closestPoint.latitud,
                longitud = closestPoint.longitud,
                precision = ubicacion.precision,
                timestamp = ubicacion.timestamp
            )
        } else {
            ubicacion
        }
    }
    
    /**
     * Proyecta un punto perpendicularmente al segmento de línea más cercano
     */
    private fun projectPointToSegment(
        point: UbicacionUsuario,
        segmentStart: PuntoRuta,
        segmentEnd: PuntoRuta
    ): PuntoRuta {
        val lat1 = Math.toRadians(segmentStart.latitud)
        val lng1 = Math.toRadians(segmentStart.longitud)
        val lat2 = Math.toRadians(segmentEnd.latitud)
        val lng2 = Math.toRadians(segmentEnd.longitud)
        val latp = Math.toRadians(point.latitud)
        val lngp = Math.toRadians(point.longitud)
        
        // Vector del segmento
        val dx = lat2 - lat1
        val dy = lng2 - lng1
        
        // Si el segmento es un punto
        if (abs(dx) < 1e-10 && abs(dy) < 1e-10) {
            return segmentStart
        }
        
        // Factor de proyección (0 = inicio, 1 = fin)
        val t = ((latp - lat1) * dx + (lngp - lng1) * dy) / (dx * dx + dy * dy)
        
        return when {
            t < 0 -> segmentStart
            t > 1 -> segmentEnd
            else -> {
                // Punto proyectado en el segmento
                val latProj = lat1 + t * dx
                val lngProj = lng1 + t * dy
                PuntoRuta(
                    latitud = Math.toDegrees(latProj),
                    longitud = Math.toDegrees(lngProj)
                )
            }
        }
    }
    
    /**
     * Calcula distancia Haversine entre dos puntos
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS * c
    }
    
    /**
     * Calcula el bearing (dirección) entre dos puntos en grados
     */
    fun calculateBearing(from: UbicacionUsuario, to: UbicacionUsuario): Double {
        val lat1 = Math.toRadians(from.latitud)
        val lat2 = Math.toRadians(to.latitud)
        val dLon = Math.toRadians(to.longitud - from.longitud)
        
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        
        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360) % 360
        
        return bearing
    }
    
    /**
     * Encuentra el índice del punto de ruta más cercano a la ubicación actual
     */
    fun findClosestRouteIndex(ubicacion: UbicacionUsuario, ruta: Ruta): Int {
        if (ruta.puntos.isEmpty()) return 0
        
        var minIndex = 0
        var minDistance = Double.MAX_VALUE
        
        ruta.puntos.forEachIndexed { index, punto ->
            val dist = haversineDistance(
                ubicacion.latitud, ubicacion.longitud,
                punto.latitud, punto.longitud
            )
            if (dist < minDistance) {
                minDistance = dist
                minIndex = index
            }
        }
        
        return minIndex
    }
}
