package com.example.regresoacasa.core.safety

import com.example.regresoacasa.core.safety.state.SafetyMode

/**
 * V3 FASE 11 — SAFETY SCORE EN TIEMPO REAL
 * 
 * Calcula un score de seguridad en tiempo real basado en múltiples factores.
 * El score indica qué tan confiable es el sistema actualmente.
 */
class SafetyScoreCalculator {
    
    /**
     * Score de seguridad
     */
    data class SafetyScore(
        val gpsReliability: Int, // 0-100
        val networkReliability: Int, // 0-100
        val systemHealth: Int, // 0-100
        val overallScore: Int // 0-100 (promedio ponderado)
    ) {
        /**
         * Verifica si el score es bueno
         */
        fun isGood(): Boolean = overallScore >= 80
        
        /**
         * Verifica si el score es aceptable
         */
        fun isAcceptable(): Boolean = overallScore >= 50
        
        /**
         * Verifica si el score es crítico
         */
        fun isCritical(): Boolean = overallScore < 50
    }
    
    /**
     * Calcula el score de seguridad
     */
    fun calculate(
        mode: SafetyMode,
        gpsAccuracy: Float?,
        hasInternet: Boolean,
        batteryLevel: Int,
        isCharging: Boolean,
        timeSinceLastHeartbeat: Long
    ): SafetyScore {
        val gpsReliability = calculateGPSReliability(mode, gpsAccuracy)
        val networkReliability = calculateNetworkReliability(mode, hasInternet)
        val systemHealth = calculateSystemHealth(
            batteryLevel,
            isCharging,
            timeSinceLastHeartbeat
        )
        
        // Ponderación: GPS 40%, Network 30%, System Health 30%
        val overallScore = (gpsReliability * 0.4 + networkReliability * 0.3 + systemHealth * 0.3).toInt()
        
        return SafetyScore(
            gpsReliability = gpsReliability,
            networkReliability = networkReliability,
            systemHealth = systemHealth,
            overallScore = overallScore
        )
    }
    
    /**
     * Calcula confiabilidad del GPS
     */
    private fun calculateGPSReliability(mode: SafetyMode, accuracy: Float?): Int {
        return when (mode) {
            SafetyMode.FULL -> {
                // GPS normal - basado en accuracy
                when {
                    accuracy == null -> 100
                    accuracy < 25f -> 100
                    accuracy < 50f -> 80
                    accuracy < 100f -> 60
                    else -> 40
                }
            }
            SafetyMode.NO_INTERNET -> {
                // Sin internet pero GPS OK
                when {
                    accuracy == null -> 90
                    accuracy < 25f -> 90
                    accuracy < 50f -> 70
                    accuracy < 100f -> 50
                    else -> 30
                }
            }
            SafetyMode.LOW_GPS -> {
                // GPS degradado
                when {
                    accuracy == null -> 40
                    accuracy < 50f -> 40
                    accuracy < 100f -> 30
                    else -> 20
                }
            }
            SafetyMode.SMS_ONLY -> {
                // Sin GPS
                0
            }
            SafetyMode.CRITICAL -> {
                // Modo crítico
                0
            }
        }
    }
    
    /**
     * Calcula confiabilidad de red
     */
    private fun calculateNetworkReliability(mode: SafetyMode, hasInternet: Boolean): Int {
        return when (mode) {
            SafetyMode.FULL -> {
                if (hasInternet) 100 else 0
            }
            SafetyMode.NO_INTERNET -> {
                0
            }
            SafetyMode.LOW_GPS -> {
                if (hasInternet) 80 else 0
            }
            SafetyMode.SMS_ONLY -> {
                // Solo SMS - red parcial
                50
            }
            SafetyMode.CRITICAL -> {
                // Modo crítico - red desconocida
                30
            }
        }
    }
    
    /**
     * Calcula salud del sistema
     */
    private fun calculateSystemHealth(
        batteryLevel: Int,
        isCharging: Boolean,
        timeSinceLastHeartbeat: Long
    ): Int {
        var score = 100
        
        // Batería
        if (isCharging) {
            score += 10 // Bono por estar cargando
        } else {
            when {
                batteryLevel <= 10 -> score -= 50
                batteryLevel <= 20 -> score -= 30
                batteryLevel <= 30 -> score -= 15
            }
        }
        
        // Heartbeat
        if (timeSinceLastHeartbeat > 90000) {
            // Más de 90 segundos sin heartbeat
            score -= 70
        } else if (timeSinceLastHeartbeat > 60000) {
            // Más de 60 segundos sin heartbeat
            score -= 40
        } else if (timeSinceLastHeartbeat > 30000) {
            // Más de 30 segundos sin heartbeat
            score -= 20
        }
        
        return score.coerceIn(0, 100)
    }
}
