package com.example.regresoacasa.utils

import android.util.Log
import timber.log.Timber

/**
 * FASE 10: Métricas internas - Logs estructurados
 * Para tracking de eventos críticos de la app
 */
object AnalyticsLogger {
    private const val TAG = "Analytics"
    
    /**
     * Log de evento estructurado
     */
    fun logEvent(eventName: String, params: Map<String, Any> = emptyMap()) {
        val logMessage = buildString {
            append("EVENT: $eventName")
            if (params.isNotEmpty()) {
                append(" | ")
                params.forEach { (key, value) ->
                    append("$key=$value")
                    if (key != params.keys.last()) append(", ")
                }
            }
        }
        Timber.tag(TAG).i(logMessage)
    }
    
    // Eventos predefinidos
    fun logRouteFailed(reason: String) {
        logEvent("ROUTE_FAILED", mapOf("reason" to reason))
    }
    
    fun logApiKeyInvalid() {
        logEvent("API_KEY_INVALID")
    }
    
    fun logGpsLost() {
        logEvent("GPS_LOST")
    }
    
    fun logNoInternet() {
        logEvent("NO_INTERNET")
    }
    
    fun logNavigationStarted(destination: String) {
        logEvent("NAVIGATION_STARTED", mapOf("destination" to destination))
    }
    
    fun logNavigationCompleted(durationSeconds: Long, distanceMeters: Double) {
        logEvent("NAVIGATION_COMPLETED", mapOf(
            "duration_seconds" to durationSeconds,
            "distance_meters" to distanceMeters
        ))
    }
    
    fun logUserAction(action: String) {
        logEvent("USER_ACTION", mapOf("action" to action))
    }
}
