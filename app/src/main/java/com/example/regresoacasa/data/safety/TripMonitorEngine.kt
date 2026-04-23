package com.example.regresoacasa.data.safety

import android.util.Log
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * TripMonitorEngine - Monitoreo inteligente durante el viaje
 * Detecta: retrasos, desviación de ruta, detención prolongada, pérdida de señal
 */
class TripMonitorEngine(
    private val scope: CoroutineScope
) {
    private val _monitorState = MutableStateFlow(MonitorState())
    val monitorState = _monitorState.asStateFlow()

    private var monitoringJob: Job? = null
    private var lastLocationUpdate: Long = System.currentTimeMillis()
    private var offRouteStartTime: Long = 0
    private var stopStartTime: Long = 0
    private var lastSpeed: Double = 0.0

    // Configuración de umbrales
    private val DELAY_THRESHOLD_MINUTES = 10
    private val OFF_ROUTE_THRESHOLD_METERS = 100.0
    private val OFF_ROUTE_TIME_THRESHOLD_MS = 10000L // 10 segundos
    private val STOP_THRESHOLD_KMH = 1.0
    private val STOP_TIME_THRESHOLD_MS = 300000L // 5 minutos
    private val SIGNAL_LOSS_THRESHOLD_MS = 60000L // 1 minuto sin GPS

    data class MonitorState(
        val isDelayDetected: Boolean = false,
        val isOffRoute: Boolean = false,
        val isProlongedStop: Boolean = false,
        val isSignalLost: Boolean = false,
        val currentSpeedKmh: Double = 0.0,
        val distanceFromRoute: Double = 0.0,
        val timeOffRoute: Long = 0,
        val timeStopped: Long = 0,
        val timeSinceLastUpdate: Long = 0
    )

    /**
     * Inicia el monitoreo del viaje
     */
    fun startMonitoring(expectedArrivalTime: Long) {
        monitoringJob?.cancel()
        monitoringJob = scope.launch(Dispatchers.Default) {
            while (true) {
                evaluateTripStatus(expectedArrivalTime)
                delay(1000) // Evaluar cada segundo
            }
        }
    }

    /**
     * Detiene el monitoreo
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        resetState()
    }

    /**
     * Procesa actualización de ubicación
     */
    fun processLocationUpdate(
        location: UbicacionUsuario,
        route: Ruta?,
        currentSpeedKmh: Double
    ) {
        lastLocationUpdate = System.currentTimeMillis()
        lastSpeed = currentSpeedKmh

        val distanceFromRoute = if (route != null) {
            calculateDistanceFromRoute(location, route)
        } else {
            0.0
        }

        // Detectar desviación de ruta
        if (distanceFromRoute > OFF_ROUTE_THRESHOLD_METERS) {
            if (offRouteStartTime == 0L) {
                offRouteStartTime = System.currentTimeMillis()
            }
            val timeOffRoute = System.currentTimeMillis() - offRouteStartTime
            _monitorState.value = _monitorState.value.copy(
                isOffRoute = true,
                distanceFromRoute = distanceFromRoute,
                timeOffRoute = timeOffRoute
            )
        } else {
            offRouteStartTime = 0L
            _monitorState.value = _monitorState.value.copy(
                isOffRoute = false,
                distanceFromRoute = distanceFromRoute,
                timeOffRoute = 0
            )
        }

        // Detectar detención prolongada
        if (currentSpeedKmh < STOP_THRESHOLD_KMH) {
            if (stopStartTime == 0L) {
                stopStartTime = System.currentTimeMillis()
            }
            val timeStopped = System.currentTimeMillis() - stopStartTime
            _monitorState.value = _monitorState.value.copy(
                isProlongedStop = timeStopped > STOP_TIME_THRESHOLD_MS,
                currentSpeedKmh = currentSpeedKmh,
                timeStopped = timeStopped
            )
        } else {
            stopStartTime = 0L
            _monitorState.value = _monitorState.value.copy(
                isProlongedStop = false,
                currentSpeedKmh = currentSpeedKmh,
                timeStopped = 0
            )
        }

        // Resetear pérdida de señal
        _monitorState.value = _monitorState.value.copy(isSignalLost = false, timeSinceLastUpdate = 0)
    }

    /**
     * Evalúa estado general del viaje
     */
    private fun evaluateTripStatus(expectedArrivalTime: Long) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastLocationUpdate

        // Detectar pérdida de señal
        val isSignalLost = timeSinceLastUpdate > SIGNAL_LOSS_THRESHOLD_MS

        // Detectar retraso
        val isDelayDetected = currentTime > expectedArrivalTime + (DELAY_THRESHOLD_MINUTES * 60 * 1000)

        _monitorState.value = _monitorState.value.copy(
            isDelayDetected = isDelayDetected,
            isSignalLost = isSignalLost,
            timeSinceLastUpdate = timeSinceLastUpdate
        )

        if (isSignalLost) {
            Log.w("TripMonitorEngine", "Señal GPS perdida por ${timeSinceLastUpdate / 1000}s")
        }
    }

    /**
     * Calcula distancia desde la ruta
     */
    private fun calculateDistanceFromRoute(location: UbicacionUsuario, route: Ruta): Double {
        if (route.puntos.isEmpty()) return 0.0

        var minDistance = Double.MAX_VALUE
        for (punto in route.puntos) {
            val distance = haversineDistance(
                location.latitud, location.longitud,
                punto.latitud, punto.longitud
            )
            if (distance < minDistance) {
                minDistance = distance
            }
        }
        return minDistance
    }

    /**
     * Fórmula de Haversine para calcular distancia entre dos puntos
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Radio de la Tierra en metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    /**
     * Reinicia el estado del monitor
     */
    private fun resetState() {
        offRouteStartTime = 0
        stopStartTime = 0
        lastLocationUpdate = System.currentTimeMillis()
        _monitorState.value = MonitorState()
    }

    /**
     * Verifica si hay alguna alerta activa
     */
    fun hasActiveAlerts(): Boolean {
        val state = _monitorState.value
        return state.isDelayDetected || 
               (state.isOffRoute && state.timeOffRoute > OFF_ROUTE_TIME_THRESHOLD_MS) ||
               state.isProlongedStop || 
               state.isSignalLost
    }

    /**
     * Obtiene el nivel de alerta actual
     */
    fun getAlertLevel(): AlertLevel {
        val state = _monitorState.value
        return when {
            state.isSignalLost && state.timeSinceLastUpdate > SIGNAL_LOSS_THRESHOLD_MS * 2 -> 
                AlertLevel.CRITICAL
            state.isProlongedStop -> 
                AlertLevel.CRITICAL
            (state.isOffRoute && state.timeOffRoute > OFF_ROUTE_TIME_THRESHOLD_MS) -> 
                AlertLevel.ACTIVE
            state.isDelayDetected -> 
                AlertLevel.SOFT
            else -> 
                AlertLevel.NONE
        }
    }
}

enum class AlertLevel {
    NONE,
    SOFT,
    ACTIVE,
    CRITICAL
}
