package com.example.regresoacasa.data.safety

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

class SuspiciousSilenceDetector(
    private val scope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "SuspiciousSilenceDetector"
        private const val CHECK_INTERVAL_MS = 30000L // Check every 30 seconds
        private const val SILENCE_THRESHOLD_MS = 300000L // 5 minutes of no interaction
        private const val IRREGULAR_MOVEMENT_THRESHOLD = 3 // Number of irregular movements to trigger
        private const val RISK_INCREASE_FACTOR = 0.15 // Risk increase per check
    }

    private val _silenceState = MutableStateFlow<SilenceState>(SilenceState.Normal)
    val silenceState = _silenceState.asStateFlow()

    private var checkJob: Job? = null
    private var isMonitoring = false

    private var lastInteractionTime: Long = System.currentTimeMillis()
    private var alertCount = 0
    private var responseCount = 0
    private var irregularMovementCount = 0
    private var currentRiskLevel: Double = 0.0

    data class SilenceState(
        val status: Status,
        val timeSinceLastInteraction: Long,
        val alertCount: Int,
        val responseCount: Int,
        val irregularMovementCount: Int,
        val riskLevel: Double
    ) {
        enum class Status {
            NORMAL,
            MONITORING,
            SUSPICIOUS,
            CRITICAL
        }

        companion object {
            val Normal = SilenceState(
                Status.NORMAL,
                0L,
                0,
                0,
                0,
                0.0
            )
        }
    }

    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring")
            return
        }

        isMonitoring = true
        resetState()
        
        checkJob = scope.launch(Dispatchers.IO) {
            while (isMonitoring) {
                checkSilence()
                delay(CHECK_INTERVAL_MS)
            }
        }

        Log.d(TAG, "Suspicious silence monitoring started")
    }

    fun stopMonitoring() {
        isMonitoring = false
        checkJob?.cancel()
        _silenceState.value = SilenceState.Normal
        Log.d(TAG, "Suspicious silence monitoring stopped")
    }

    fun recordUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        responseCount++
        Log.d(TAG, "User interaction recorded. Total responses: $responseCount")
    }

    fun recordAlertSent() {
        alertCount++
        Log.d(TAG, "Alert sent recorded. Total alerts: $alertCount")
    }

    fun recordIrregularMovement() {
        irregularMovementCount++
        Log.d(TAG, "Irregular movement recorded. Count: $irregularMovementCount")
    }

    private fun resetState() {
        lastInteractionTime = System.currentTimeMillis()
        alertCount = 0
        responseCount = 0
        irregularMovementCount = 0
        currentRiskLevel = 0.0
        _silenceState.value = SilenceState.Normal
    }

    private suspend fun checkSilence() {
        val now = System.currentTimeMillis()
        val timeSinceLastInteraction = now - lastInteractionTime
        val responseRatio = if (alertCount > 0) responseCount.toDouble() / alertCount.toDouble() else 1.0

        val status = determineStatus(timeSinceLastInteraction, responseRatio, irregularMovementCount)
        
        currentRiskLevel = calculateRiskLevel(
            timeSinceLastInteraction,
            responseRatio,
            irregularMovementCount
        )

        val newState = SilenceState(
            status = status,
            timeSinceLastInteraction = timeSinceLastInteraction,
            alertCount = alertCount,
            responseCount = responseCount,
            irregularMovementCount = irregularMovementCount,
            riskLevel = currentRiskLevel
        )

        _silenceState.value = newState

        when (status) {
            SilenceState.Status.CRITICAL -> {
                Log.e(TAG, "CRITICAL: Suspicious silence detected - Risk: $currentRiskLevel")
            }
            SilenceState.Status.SUSPICIOUS -> {
                Log.w(TAG, "SUSPICIOUS: User not responding normally - Risk: $currentRiskLevel")
            }
            SilenceState.Status.MONITORING -> {
                Log.d(TAG, "MONITORING: Tracking user interaction patterns")
            }
            SilenceState.Status.NORMAL -> {
                Log.d(TAG, "NORMAL: User interaction patterns normal")
            }
        }
    }

    private fun determineStatus(
        timeSinceLastInteraction: Long,
        responseRatio: Double,
        irregularMovements: Int
    ): SilenceState.Status {
        val isSilent = timeSinceLastInteraction > SILENCE_THRESHOLD_MS
        val isUnresponsive = responseRatio < 0.3
        val hasIrregularMovement = irregularMovements >= IRREGULAR_MOVEMENT_THRESHOLD

        return when {
            isSilent && isUnresponsive && hasIrregularMovement -> SilenceState.Status.CRITICAL
            isSilent && isUnresponsive -> SilenceState.Status.SUSPICIOUS
            isSilent || hasIrregularMovement -> SilenceState.Status.MONITORING
            else -> SilenceState.Status.NORMAL
        }
    }

    private fun calculateRiskLevel(
        timeSinceLastInteraction: Long,
        responseRatio: Double,
        irregularMovements: Int
    ): Double {
        var risk = 0.0

        // Risk from silence
        if (timeSinceLastInteraction > SILENCE_THRESHOLD_MS) {
            val silenceMinutes = timeSinceLastInteraction / 60000.0
            risk += kotlin.math.min(silenceMinutes / 30.0 * 0.4, 0.4) // Max 0.4 from silence
        }

        // Risk from lack of response
        if (responseRatio < 0.5) {
            risk += (0.5 - responseRatio) * 0.8 // Max 0.4 from unresponsiveness
        }

        // Risk from irregular movements
        risk += kotlin.math.min(irregularMovements * 0.15, 0.3) // Max 0.3 from irregular movements

        return kotlin.math.min(risk, 1.0)
    }

    fun shouldEscalateAlert(): Boolean {
        val state = _silenceState.value
        return state.status == SilenceState.Status.CRITICAL || 
               (state.status == SilenceState.Status.SUSPICIOUS && state.riskLevel > 0.7)
    }

    fun getEscalationReason(): String {
        val state = _silenceState.value
        return buildString {
            append("Riesgo de silencio sospechoso: ${(state.riskLevel * 100).toInt()}%")
            appendLine()
            append("Tiempo sin interacción: ${state.timeSinceLastInteraction / 60000} min")
            appendLine()
            append("Alertas enviadas: ${state.alertCount}, Respuestas: ${state.responseCount}")
            appendLine()
            append("Movimientos irregulares: ${state.irregularMovementCount}")
        }
    }

    fun getCurrentRiskLevel(): Double {
        return _silenceState.value.riskLevel
    }

    fun isUserResponsive(): Boolean {
        val state = _silenceState.value
        return state.responseRatio() >= 0.5
    }

    private fun SilenceState.responseRatio(): Double {
        return if (alertCount > 0) responseCount.toDouble() / alertCount.toDouble() else 1.0
    }
}
