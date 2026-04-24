package com.example.regresoacasa.core.safety.navigation

import com.example.regresoacasa.domain.model.UbicacionUsuario

/**
 * V3 FASE 12 — FALLBACK REAL (NO FAKE)
 * 
 * Sistema de fallback que NO usa línea recta.
 * En su lugar, usa:
 * - Última ruta válida cacheada
 * - Heading + distancia
 * - Modo guía mínima
 */
class FallbackNavigator {
    
    /**
     * Ruta cacheada para fallback
     */
    private var cachedRoute: List<UbicacionUsuario>? = null
    private var lastValidLocation: UbicacionUsuario? = null
    private var destination: UbicacionUsuario? = null
    
    /**
     * Guarda la ruta actual para fallback
     */
    fun cacheRoute(route: List<UbicacionUsuario>) {
        cachedRoute = route
    }
    
    /**
     * Guarda la última ubicación válida
     */
    fun updateLastValidLocation(location: UbicacionUsuario) {
        lastValidLocation = location
    }
    
    /**
     * Guarda el destino
     */
    fun setDestination(dest: UbicacionUsuario) {
        destination = dest
    }
    
    /**
     * Obtiene instrucciones de fallback
     * 
     * NO usa línea recta. En su lugar:
     * 1. Si hay ruta cacheada, usa la última parte
     * 2. Si no, usa heading + distancia
     * 3. Si no, usa modo guía mínima
     */
    fun getFallbackInstructions(): FallbackInstructions {
        val lastLoc = lastValidLocation
        val dest = destination
        
        if (lastLoc == null || dest == null) {
            return FallbackInstructions(
                type = FallbackType.MINIMAL_GUIDE,
                message = "Sin ubicación o destino. Modo guía mínima.",
                heading = null,
                distance = null,
                cachedSegment = null
            )
        }
        
        // Opción 1: Usar ruta cacheada
        val cached = cachedRoute
        if (cached != null && cached.isNotEmpty()) {
            // Encontrar el segmento más cercano a la última ubicación
            val segment = findClosestSegment(lastLoc, cached)
            
            if (segment != null) {
                return FallbackInstructions(
                    type = FallbackType.CACHED_ROUTE,
                    message = "Usando última ruta conocida.",
                    heading = calculateHeading(lastLoc, segment.first()),
                    distance = calculateDistance(lastLoc, dest),
                    cachedSegment = segment
                )
            }
        }
        
        // Opción 2: Usar heading + distancia
        val heading = calculateHeading(lastLoc, dest)
        val distance = calculateDistance(lastLoc, dest)
        
        return FallbackInstructions(
            type = FallbackType.HEADING_DISTANCE,
            message = "Continuar en dirección $heading° por ${distance.toInt()}m",
            heading = heading,
            distance = distance,
            cachedSegment = null
        )
    }
    
    /**
     * Encuentra el segmento de ruta más cercano a una ubicación
     */
    private fun findClosestSegment(
        location: UbicacionUsuario,
        route: List<UbicacionUsuario>
    ): List<UbicacionUsuario>? {
        var minDistance = Float.MAX_VALUE
        var closestIndex = -1
        
        for (i in 0 until route.size - 1) {
            val segmentStart = route[i]
            val distance = calculateDistance(location, segmentStart)
            
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = i
            }
        }
        
        if (closestIndex >= 0) {
            // Retornar desde el punto más cercano hasta el final
            return route.subList(closestIndex, route.size)
        }
        
        return null
    }
    
    /**
     * Calcula el heading entre dos ubicaciones
     */
    private fun calculateHeading(from: UbicacionUsuario, to: UbicacionUsuario): Float {
        val lat1 = Math.toRadians(from.latitud)
        val lon1 = Math.toRadians(from.longitud)
        val lat2 = Math.toRadians(to.latitud)
        val lon2 = Math.toRadians(to.longitud)
        
        val dLon = lon2 - lon1
        
        val x = Math.sin(dLon) * Math.cos(lat2)
        val y = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        
        val bearing = Math.toDegrees(Math.atan2(x, y))
        
        return ((bearing + 360) % 360).toFloat()
    }
    
    /**
     * Calcula la distancia entre dos ubicaciones en metros
     */
    private fun calculateDistance(from: UbicacionUsuario, to: UbicacionUsuario): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            from.latitud,
            from.longitud,
            to.latitud,
            to.longitud,
            results
        )
        return results[0]
    }
    
    /**
     * Limpia el cache
     */
    fun clearCache() {
        cachedRoute = null
        lastValidLocation = null
        destination = null
    }
    
    /**
     * Tipo de fallback
     */
    enum class FallbackType {
        /**
         * Usa última ruta cacheada
         */
        CACHED_ROUTE,
        
        /**
         * Usa heading + distancia
         */
        HEADING_DISTANCE,
        
        /**
         * Modo guía mínima
         */
        MINIMAL_GUIDE
    }
    
    /**
     * Instrucciones de fallback
     */
    data class FallbackInstructions(
        val type: FallbackType,
        val message: String,
        val heading: Float?,
        val distance: Float?,
        val cachedSegment: List<UbicacionUsuario>?
    )
}
