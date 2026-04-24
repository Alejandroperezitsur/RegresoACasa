package com.example.regresoacasa.core.performance

import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.model.PuntoRuta
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SpatialIndex(private val points: List<PuntoRuta>) {
    
    private val grid = mutableMapOf<String, MutableList<PuntoRuta>>()
    private val cellSize = 0.001 // ~100m at equator
    
    init {
        buildGrid()
    }
    
    private fun buildGrid() {
        points.forEach { point ->
            val cellKey = getCellKey(point.latitud, point.longitud)
            grid.getOrPut(cellKey) { mutableListOf() }.add(point)
        }
    }
    
    private fun getCellKey(lat: Double, lon: Double): String {
        val latCell = (lat / cellSize).toInt()
        val lonCell = (lon / cellSize).toInt()
        return "$latCell,$lonCell"
    }
    
    fun findNearest(location: UbicacionUsuario, maxDistance: Double = 0.01): PuntoRuta? {
        val lat = location.latitud
        val lon = location.longitud
        
        // Get current cell and surrounding cells
        val latCell = (lat / cellSize).toInt()
        val lonCell = (lon / cellSize).toInt()
        
        val cellsToCheck = mutableListOf<String>()
        
        // Check 3x3 grid around current cell
        for (dLat in -1..1) {
            for (dLon in -1..1) {
                cellsToCheck.add("${latCell + dLat},${lonCell + dLon}")
            }
        }
        
        var nearest: PuntoRuta? = null
        var minDistance = Double.MAX_VALUE
        
        cellsToCheck.forEach { cellKey ->
            grid[cellKey]?.forEach { point ->
                val dist = haversineDistance(lat, lon, point.latitud, point.longitud)
                if (dist < minDistance && dist < maxDistance) {
                    minDistance = dist
                    nearest = point
                }
            }
        }
        
        return nearest
    }
    
    fun findPointsInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<PuntoRuta> {
        val result = mutableListOf<PuntoRuta>()
        
        val minLatCell = (minLat / cellSize).toInt()
        val maxLatCell = (maxLat / cellSize).toInt()
        val minLonCell = (minLon / cellSize).toInt()
        val maxLonCell = (maxLon / cellSize).toInt()
        
        for (latCell in minLatCell..maxLatCell) {
            for (lonCell in minLonCell..maxLonCell) {
                grid["$latCell,$lonCell"]?.let { points ->
                    result.addAll(points.filter { point ->
                        point.latitud >= minLat && point.latitud <= maxLat &&
                        point.longitud >= minLon && point.longitud <= maxLon
                    })
                }
            }
        }
        
        return result
    }
    
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}

class BoundingBoxFilter {
    
    companion object {
        fun filterPoints(
            location: UbicacionUsuario,
            points: List<PuntoRuta>,
            radiusMeters: Double = 500.0
        ): List<PuntoRuta> {
            val lat = location.latitud
            val lon = location.longitud
            
            // Approximate meters per degree
            val metersPerLat = 111320.0
            val metersPerLon = 111320.0 * Math.cos(Math.toRadians(lat))
            
            val latDelta = radiusMeters / metersPerLat
            val lonDelta = radiusMeters / metersPerLon
            
            val minLat = lat - latDelta
            val maxLat = lat + latDelta
            val minLon = lon - lonDelta
            val maxLon = lon + lonDelta
            
            return points.filter { point ->
                point.latitud >= minLat && point.latitud <= maxLat &&
                point.longitud >= minLon && point.longitud <= maxLon
            }
        }
    }
}
