package com.example.regresoacasa.core.safety.location

import android.location.Location
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * V3 FASE 5 — GPS SPOOFING DETECTION AVANZADO
 * 
 * Sistema de detección de spoofing con múltiples factores:
 * - Velocidad anómala
 * - Saltos irreales
 * - Mock provider
 * - Variación de señal
 * - Consistencia temporal
 * 
 * Genera un score de spoofing. Si score >= 2, marca como no confiable.
 */
class SpoofingDetector {
    
    private val locationHistory = mutableListOf<Location>()
    private val maxHistorySize = 20
    
    /**
     * Análisis de spoofing
     */
    data class SpoofingAnalysis(
        val speedAnomaly: Boolean,
        val jumpAnomaly: Boolean,
        val mockProvider: Boolean,
        val signalVariance: Boolean,
        val temporalInconsistency: Boolean,
        val score: Int // 0-5, más alto = más probable spoofing
    ) {
        /**
         * Verifica si es spoofing (score >= 2)
         */
        fun isSpoofing(): Boolean = score >= 2
        
        /**
         * Verifica si es spoofing severo (score >= 4)
         */
        fun isSevereSpoofing(): Boolean = score >= 4
    }
    
    /**
     * Analiza una ubicación para detectar spoofing
     */
    fun analyze(location: Location): SpoofingAnalysis {
        // Agregar al historial
        locationHistory.add(location)
        if (locationHistory.size > maxHistorySize) {
            locationHistory.removeAt(0)
        }
        
        var score = 0
        
        // 1. Verificar mock provider
        val mockProvider = isMockProvider(location)
        if (mockProvider) score += 2
        
        // 2. Verificar velocidad anómala
        val speedAnomaly = hasSpeedAnomaly(location)
        if (speedAnomaly) score += 1
        
        // 3. Verificar saltos irreales
        val jumpAnomaly = hasJumpAnomaly(location)
        if (jumpAnomaly) score += 1
        
        // 4. Verificar variación de señal
        val signalVariance = hasSignalVariance(location)
        if (signalVariance) score += 1
        
        // 5. Verificar inconsistencia temporal
        val temporalInconsistency = hasTemporalInconsistency(location)
        if (temporalInconsistency) score += 1
        
        return SpoofingAnalysis(
            speedAnomaly = speedAnomaly,
            jumpAnomaly = jumpAnomaly,
            mockProvider = mockProvider,
            signalVariance = signalVariance,
            temporalInconsistency = temporalInconsistency,
            score = score
        )
    }
    
    /**
     * Verifica si el provider indica que es mock
     */
    private fun isMockProvider(location: Location): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            location.isFromMockProvider
        } else {
            false
        }
    }
    
    /**
     * Verifica velocidad anómala
     * 
     * Velocidades humanas normales: 0-10 m/s (36 km/h)
     * Velocidades vehículo: 0-40 m/s (144 km/h)
     * Velocidades irreales: > 100 m/s (360 km/h)
     */
    private fun hasSpeedAnomaly(location: Location): Boolean {
        val speed = location.speed // m/s
        
        // Velocidad extremadamente alta
        if (speed > 100.0) return true
        
        // Si hay historial, verificar cambio brusco de velocidad
        if (locationHistory.size >= 2) {
            val prev = locationHistory[locationHistory.size - 2]
            val prevSpeed = prev.speed
            val speedChange = abs(speed - prevSpeed)
            
            // Cambio de velocidad > 20 m/s en < 1 segundo es irreales
            val timeDiff = (location.time - prev.time) / 1000.0
            if (timeDiff > 0 && timeDiff < 1.0 && speedChange > 20.0) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Verifica saltos irreales de ubicación
     * 
     * Un salto > 1km en < 10 segundos es irreales
     * Un salto > 100m en < 1 segundo es sospechoso
     */
    private fun hasJumpAnomaly(location: Location): Boolean {
        if (locationHistory.size < 2) return false
        
        val prev = locationHistory[locationHistory.size - 2]
        val distance = distanceBetween(prev, location)
        val timeDiff = (location.time - prev.time) / 1000.0 // segundos
        
        if (timeDiff <= 0) return false
        
        val velocity = distance / timeDiff // m/s
        
        // Velocidad > 100 m/s es irreales
        if (velocity > 100.0) return true
        
        // Velocidad > 50 m/s es sospechosa
        if (velocity > 50.0) return true
        
        // Salto > 1km en < 10 segundos
        if (distance > 1000 && timeDiff < 10) return true
        
        // Salto > 100m en < 1 segundo
        if (distance > 100 && timeDiff < 1) return true
        
        return false
    }
    
    /**
     * Verifica variación de señal (accuracy)
     * 
     * Variaciones extremas de accuracy pueden indicar spoofing
     */
    private fun hasSignalVariance(location: Location): Boolean {
        if (locationHistory.size < 5) return false
        
        val accuracies = locationHistory.takeLast(5).mapNotNull { it.accuracy }
        if (accuracies.size < 5) return false
        
        val avgAccuracy = accuracies.average()
        val currentAccuracy = location.accuracy
        
        // Si la accuracy actual es > 3x el promedio, es sospechoso
        if (currentAccuracy > avgAccuracy * 3) return true
        
        // Si la accuracy es perfecta (0.0) pero el historial es pobre, es sospechoso
        if (currentAccuracy == 0.0f && avgAccuracy > 50.0) return true
        
        return false
    }
    
    /**
     * Verifica inconsistencia temporal
     * 
     * Los timestamps deben ser monótonamente crecientes
     * El intervalo entre actualizaciones debe ser razonable
     */
    private fun hasTemporalInconsistency(location: Location): Boolean {
        if (locationHistory.size < 2) return false
        
        val prev = locationHistory[locationHistory.size - 2]
        
        // Timestamp debe ser mayor que el anterior
        if (location.time <= prev.time) return true
        
        // Si hay 3+ ubicaciones, verificar consistencia de intervalos
        if (locationHistory.size >= 3) {
            val prev2 = locationHistory[locationHistory.size - 3]
            val interval1 = prev.time - prev2.time
            val interval2 = location.time - prev.time
            
            // Si los intervalos varían dramáticamente, es sospechoso
            val ratio = if (interval1 > 0) interval2.toDouble() / interval1 else 0.0
            if (ratio > 10.0 || ratio < 0.1) return true
        }
        
        return false
    }
    
    /**
     * Calcula distancia entre dos ubicaciones en metros
     */
    private fun distanceBetween(loc1: Location, loc2: Location): Float {
        return loc1.distanceTo(loc2)
    }
    
    /**
     * Limpia el historial
     */
    fun clearHistory() {
        locationHistory.clear()
    }
    
    /**
     * Obtiene el tamaño del historial
     */
    fun getHistorySize(): Int = locationHistory.size
}
