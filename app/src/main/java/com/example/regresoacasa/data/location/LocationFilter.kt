package com.example.regresoacasa.data.location

import com.example.regresoacasa.domain.model.UbicacionUsuario
import java.util.ArrayDeque

/**
 * Filtro de ubicación con Moving Average para suavizar saltos de GPS
 */
class LocationFilter(private val windowSize: Int = 5) {
    private val latitudes = ArrayDeque<Double>(windowSize)
    private val longitudes = ArrayDeque<Double>(windowSize)
    private val precisions = ArrayDeque<Float>(windowSize)
    
    /**
     * Aplica filtro de promedio móvil a la ubicación
     * @param location Ubicación cruda del GPS
     * @return Ubicación suavizada
     */
    fun filter(location: UbicacionUsuario): UbicacionUsuario {
        // Agregar a ventanas
        latitudes.addLast(location.latitud)
        longitudes.addLast(location.longitud)
        precisions.addLast(location.precision)
        
        // Mantener tamaño de ventana
        if (latitudes.size > windowSize) {
            latitudes.removeFirst()
            longitudes.removeFirst()
            precisions.removeFirst()
        }
        
        // Calcular promedios ponderados por precisión (menor precisión = más peso)
        var totalWeight = 0.0
        var weightedLat = 0.0
        var weightedLng = 0.0
        
        val precisionArray = precisions.toFloatArray()
        val latArray = latitudes.toDoubleArray()
        val lngArray = longitudes.toDoubleArray()
        
        for (i in precisionArray.indices) {
            // Peso inversamente proporcional a la precisión (menor precisión = más peso)
            val weight = 1.0 / (precisionArray[i] + 1.0)
            weightedLat += latArray[i] * weight
            weightedLng += lngArray[i] * weight
            totalWeight += weight
        }
        
        return UbicacionUsuario(
            latitud = weightedLat / totalWeight,
            longitud = weightedLng / totalWeight,
            precision = location.precision,
            timestamp = location.timestamp
        )
    }
    
    /**
     * Reinicia el filtro (útil al iniciar nueva navegación)
     */
    fun reset() {
        latitudes.clear()
        longitudes.clear()
        precisions.clear()
    }
    
    /**
     * Verifica si hay suficientes muestras para filtrar
     */
    fun hasEnoughSamples(): Boolean = latitudes.size >= windowSize / 2
}
