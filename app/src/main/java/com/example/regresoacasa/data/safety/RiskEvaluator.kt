package com.example.regresoacasa.data.safety

import android.util.Log
import kotlin.math.max
import kotlin.math.min

class RiskEvaluator {
    
    companion object {
        private const val TAG = "RiskEvaluator"
        
        // Risk level thresholds
        private const val NORMAL_THRESHOLD = 0.3
        private const val WARNING_THRESHOLD = 0.6
        private const val DANGER_THRESHOLD = 0.8
    }

    data class RiskInputs(
        val delayMs: Long,
        val deviationMeters: Double,
        val stopDurationMs: Long,
        val signalLossDurationMs: Long,
        val currentSpeed: Float,
        val batteryLevel: Int,
        val recentAlerts: Int,
        val userInteractions: Int,
        val tripDurationMs: Long
    )

    data class RiskEvaluation(
        val riskScore: Double, // 0.0 to 1.0
        val riskLevel: RiskLevel,
        val factors: RiskFactors
    )

    data class RiskFactors(
        val delayRisk: Double,
        val deviationRisk: Double,
        val stopRisk: Double,
        val signalRisk: Double,
        val batteryRisk: Double,
        val silenceRisk: Double
    )

    enum class RiskLevel {
        NORMAL,
        WARNING,
        DANGER,
        CRITICAL
    }

    fun evaluateRisk(inputs: RiskInputs): RiskEvaluation {
        val delayRisk = calculateDelayRisk(inputs.delayMs, inputs.tripDurationMs)
        val deviationRisk = calculateDeviationRisk(inputs.deviationMeters, inputs.currentSpeed)
        val stopRisk = calculateStopRisk(inputs.stopDurationMs, inputs.currentSpeed)
        val signalRisk = calculateSignalRisk(inputs.signalLossDurationMs)
        val batteryRisk = calculateBatteryRisk(inputs.batteryLevel)
        val silenceRisk = calculateSilenceRisk(inputs.recentAlerts, inputs.userInteractions, inputs.tripDurationMs)

        val factors = RiskFactors(
            delayRisk = delayRisk,
            deviationRisk = deviationRisk,
            stopRisk = stopRisk,
            signalRisk = signalRisk,
            batteryRisk = batteryRisk,
            silenceRisk = silenceRisk
        )

        val riskScore = calculateOverallRisk(factors)
        val riskLevel = determineRiskLevel(riskScore)

        Log.d(TAG, "Risk evaluation - Score: $riskScore, Level: $riskLevel, Factors: $factors")

        return RiskEvaluation(
            riskScore = riskScore,
            riskLevel = riskLevel,
            factors = factors
        )
    }

    private fun calculateDelayRisk(delayMs: Long, tripDurationMs: Long): Double {
        if (delayMs <= 0) return 0.0
        
        // Normalize delay relative to trip duration
        val tripRatio = if (tripDurationMs > 0) delayMs.toDouble() / tripDurationMs.toDouble() else 0.0
        
        // Base risk from absolute delay
        val absoluteRisk = when {
            delayMs < 300000L -> 0.0 // < 5 minutes
            delayMs < 600000L -> 0.2 // 5-10 minutes
            delayMs < 1200000L -> 0.5 // 10-20 minutes
            delayMs < 1800000L -> 0.7 // 20-30 minutes
            else -> 0.9 // > 30 minutes
        }
        
        // Combine absolute and relative risk
        val relativeRisk = min(tripRatio * 2.0, 1.0)
        
        return max(absoluteRisk, relativeRisk * 0.5)
    }

    private fun calculateDeviationRisk(deviationMeters: Double, currentSpeed: Float): Double {
        if (deviationMeters <= 0) return 0.0
        
        // Adaptive threshold based on speed
        val adaptiveThreshold = when {
            currentSpeed < 2.0f -> 50.0 // Walking
            currentSpeed < 5.0f -> 100.0 // Mixed
            else -> 200.0 // Driving
        }
        
        val ratio = deviationMeters / adaptiveThreshold
        
        return when {
            ratio < 0.5 -> 0.0
            ratio < 1.0 -> ratio * 0.3
            ratio < 2.0 -> 0.3 + (ratio - 1.0) * 0.4
            else -> min(0.7 + (ratio - 2.0) * 0.15, 1.0)
        }
    }

    private fun calculateStopRisk(stopDurationMs: Long, currentSpeed: Float): Double {
        if (stopDurationMs <= 0) return 0.0
        
        // Adaptive threshold based on speed
        val adaptiveThreshold = when {
            currentSpeed < 2.0f -> 120000L // 2 minutes walking
            currentSpeed < 5.0f -> 180000L // 3 minutes mixed
            else -> 300000L // 5 minutes driving
        }
        
        val ratio = stopDurationMs.toDouble() / adaptiveThreshold.toDouble()
        
        return when {
            ratio < 0.5 -> 0.0
            ratio < 1.0 -> ratio * 0.4
            ratio < 2.0 -> 0.4 + (ratio - 1.0) * 0.3
            else -> min(0.7 + (ratio - 2.0) * 0.1, 1.0)
        }
    }

    private fun calculateSignalRisk(signalLossDurationMs: Long): Double {
        if (signalLossDurationMs <= 0) return 0.0
        
        return when {
            signalLossDurationMs < 30000L -> 0.1 // < 30 seconds
            signalLossDurationMs < 60000L -> 0.3 // 30-60 seconds
            signalLossDurationMs < 120000L -> 0.5 // 1-2 minutes
            signalLossDurationMs < 300000L -> 0.7 // 2-5 minutes
            else -> 0.9 // > 5 minutes
        }
    }

    private fun calculateBatteryRisk(batteryLevel: Int): Double {
        if (batteryLevel >= 50) return 0.0
        
        return when {
            batteryLevel >= 30 -> 0.2
            batteryLevel >= 20 -> 0.4
            batteryLevel >= 15 -> 0.6
            batteryLevel >= 10 -> 0.8
            else -> 1.0
        }
    }

    private fun calculateSilenceRisk(
        recentAlerts: Int,
        userInteractions: Int,
        tripDurationMs: Long
    ): Double {
        if (recentAlerts == 0) return 0.0
        
        // Ratio of interactions to alerts
        val responseRatio = if (recentAlerts > 0) {
            userInteractions.toDouble() / recentAlerts.toDouble()
        } else {
            1.0
        }
        
        // Base risk from lack of response
        val silenceRisk = when {
            responseRatio >= 0.8 -> 0.0
            responseRatio >= 0.5 -> 0.3
            responseRatio >= 0.2 -> 0.6
            else -> 0.9
        }
        
        // Increase risk if trip is long and user is silent
        val durationFactor = if (tripDurationMs > 3600000L) { // > 1 hour
            0.2
        } else {
            0.0
        }
        
        return min(silenceRisk + durationFactor, 1.0)
    }

    private fun calculateOverallRisk(factors: RiskFactors): Double {
        // Weighted average of all factors
        val weights = mapOf(
            "delay" to 0.25,
            "deviation" to 0.20,
            "stop" to 0.20,
            "signal" to 0.15,
            "battery" to 0.10,
            "silence" to 0.10
        )
        
        val weightedSum = 
            factors.delayRisk * (weights["delay"] ?: 0.25) +
            factors.deviationRisk * (weights["deviation"] ?: 0.20) +
            factors.stopRisk * (weights["stop"] ?: 0.20) +
            factors.signalRisk * (weights["signal"] ?: 0.15) +
            factors.batteryRisk * (weights["battery"] ?: 0.10) +
            factors.silenceRisk * (weights["silence"] ?: 0.10)
        
        // Apply non-linear scaling to emphasize high risks
        return when {
            weightedSum < 0.3 -> weightedSum
            weightedSum < 0.6 -> weightedSum * 1.1
            weightedSum < 0.8 -> weightedSum * 1.2
            else -> min(weightedSum * 1.3, 1.0)
        }
    }

    private fun determineRiskLevel(riskScore: Double): RiskLevel {
        return when {
            riskScore < NORMAL_THRESHOLD -> RiskLevel.NORMAL
            riskScore < WARNING_THRESHOLD -> RiskLevel.WARNING
            riskScore < DANGER_THRESHOLD -> RiskLevel.DANGER
            else -> RiskLevel.CRITICAL
        }
    }

    fun shouldTriggerAlert(evaluation: RiskEvaluation): Boolean {
        return evaluation.riskLevel != RiskLevel.NORMAL
    }

    fun getAlertMessage(evaluation: RiskEvaluation): String {
        return when (evaluation.riskLevel) {
            RiskLevel.NORMAL -> "Todo va bien"
            RiskLevel.WARNING -> "Precaución: Detectamos una situación inusual"
            RiskLevel.DANGER -> "Alerta: Tu seguridad podría estar comprometida"
            RiskLevel.CRITICAL -> "EMERGENCIA: Se detectó una situación crítica"
        }
    }
}
