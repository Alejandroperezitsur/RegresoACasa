package com.example.regresoacasa.data.safety

import android.util.Log
import kotlin.math.max
import kotlin.math.min

class AdaptiveAlertThresholds {
    
    companion object {
        private const val TAG = "AdaptiveThresholds"
        
        // Speed thresholds (m/s)
        private const val WALKING_SPEED_MAX = 2.0
        private const val DRIVING_SPEED_MIN = 5.0
        private const val DRIVING_SPEED_MAX = 30.0
        
        // Base thresholds (in meters)
        private const val BASE_DEVIATION_THRESHOLD_WALKING = 50.0
        private const val BASE_DEVIATION_THRESHOLD_DRIVING = 200.0
        
        // Base stop time thresholds (in milliseconds)
        private const val BASE_STOP_TIME_WALKING = 120000L // 2 minutes
        private const val BASE_STOP_TIME_DRIVING = 300000L // 5 minutes
        
        // Factors
        private const val DEVIATION_SPEED_FACTOR = 10.0 // meters per m/s
        private const val STOP_TIME_SPEED_FACTOR = 30000L // ms per m/s
    }

    enum class TravelMode {
        WALKING,
        DRIVING,
        UNKNOWN
    }

    data class Thresholds(
        val deviationThreshold: Double, // meters
        val stopTimeThreshold: Long, // milliseconds
        val delayThreshold: Long, // milliseconds
        val travelMode: TravelMode
    )

    fun calculateThresholds(
        currentSpeed: Float, // m/s
        averageSpeed: Float, // m/s
        tripDuration: Long, // milliseconds
        historicalDeviations: List<Double> // meters
    ): Thresholds {
        val travelMode = determineTravelMode(currentSpeed, averageSpeed)
        
        val deviationThreshold = calculateDeviationThreshold(
            currentSpeed,
            travelMode,
            historicalDeviations
        )
        
        val stopTimeThreshold = calculateStopTimeThreshold(
            currentSpeed,
            travelMode,
            tripDuration
        )
        
        val delayThreshold = calculateDelayThreshold(
            currentSpeed,
            travelMode,
            tripDuration
        )
        
        Log.d(TAG, "Calculated thresholds - Mode: $travelMode, Deviation: ${deviationThreshold}m, Stop: ${stopTimeThreshold}ms, Delay: ${delayThreshold}ms")
        
        return Thresholds(
            deviationThreshold = deviationThreshold,
            stopTimeThreshold = stopTimeThreshold,
            delayThreshold = delayThreshold,
            travelMode = travelMode
        )
    }

    private fun determineTravelMode(currentSpeed: Float, averageSpeed: Float): TravelMode {
        val effectiveSpeed = max(currentSpeed, averageSpeed)
        
        return when {
            effectiveSpeed < WALKING_SPEED_MAX -> TravelMode.WALKING
            effectiveSpeed >= DRIVING_SPEED_MIN -> TravelMode.DRIVING
            else -> TravelMode.UNKNOWN
        }
    }

    private fun calculateDeviationThreshold(
        currentSpeed: Float,
        travelMode: TravelMode,
        historicalDeviations: List<Double>
    ): Double {
        val baseThreshold = when (travelMode) {
            TravelMode.WALKING -> BASE_DEVIATION_THRESHOLD_WALKING
            TravelMode.DRIVING -> BASE_DEVIATION_THRESHOLD_DRIVING
            TravelMode.UNKNOWN -> (BASE_DEVIATION_THRESHOLD_WALKING + BASE_DEVIATION_THRESHOLD_DRIVING) / 2
        }
        
        // Adaptive component based on speed
        val speedComponent = currentSpeed * DEVIATION_SPEED_FACTOR
        
        // Adaptive component based on history
        val historyComponent = if (historicalDeviations.isNotEmpty()) {
            val avgDeviation = historicalDeviations.average()
            val maxDeviation = historicalDeviations.maxOrNull() ?: 0.0
            (avgDeviation + maxDeviation) / 2 * 0.3 // 30% weight on history
        } else {
            0.0
        }
        
        val calculatedThreshold = baseThreshold + speedComponent + historyComponent
        
        // Clamp to reasonable bounds
        return when (travelMode) {
            TravelMode.WALKING -> min(max(calculatedThreshold, 30.0), 150.0)
            TravelMode.DRIVING -> min(max(calculatedThreshold, 100.0), 500.0)
            TravelMode.UNKNOWN -> min(max(calculatedThreshold, 50.0), 300.0)
        }
    }

    private fun calculateStopTimeThreshold(
        currentSpeed: Float,
        travelMode: TravelMode,
        tripDuration: Long
    ): Long {
        val baseThreshold = when (travelMode) {
            TravelMode.WALKING -> BASE_STOP_TIME_WALKING
            TravelMode.DRIVING -> BASE_STOP_TIME_DRIVING
            TravelMode.UNKNOWN -> (BASE_STOP_TIME_WALKING + BASE_STOP_TIME_DRIVING) / 2
        }
        
        // Adaptive component based on speed
        val speedComponent = (currentSpeed * STOP_TIME_SPEED_FACTOR).toLong()
        
        // Adaptive component based on trip duration
        // Longer trips may have more legitimate stops
        val tripDurationComponent = if (tripDuration > 1800000L) { // > 30 minutes
            60000L // Add 1 minute for long trips
        } else {
            0L
        }
        
        val calculatedThreshold = baseThreshold + speedComponent + tripDurationComponent
        
        // Clamp to reasonable bounds
        return when (travelMode) {
            TravelMode.WALKING -> min(max(calculatedThreshold, 60000L), 300000L) // 1-5 minutes
            TravelMode.DRIVING -> min(max(calculatedThreshold, 120000L), 600000L) // 2-10 minutes
            TravelMode.UNKNOWN -> min(max(calculatedThreshold, 90000L), 450000L) // 1.5-7.5 minutes
        }
    }

    private fun calculateDelayThreshold(
        currentSpeed: Float,
        travelMode: TravelMode,
        tripDuration: Long
    ): Long {
        // Delay threshold is typically 2-3x the stop time threshold
        val stopTimeThreshold = calculateStopTimeThreshold(currentSpeed, travelMode, tripDuration)
        
        return (stopTimeThreshold * 2.5).toLong()
    }

    fun shouldTriggerDeviationAlert(
        currentDeviation: Double,
        thresholds: Thresholds
    ): Boolean {
        return currentDeviation > thresholds.deviationThreshold
    }

    fun shouldTriggerStopAlert(
        stopDuration: Long,
        thresholds: Thresholds
    ): Boolean {
        return stopDuration > thresholds.stopTimeThreshold
    }

    fun shouldTriggerDelayAlert(
        delayDuration: Long,
        thresholds: Thresholds
    ): Boolean {
        return delayDuration > thresholds.delayThreshold
    }
}
