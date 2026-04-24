package com.example.regresoacasa.core

import android.content.Context
import com.example.regresoacasa.core.emergency.EmergencyManager
import com.example.regresoacasa.core.location.LocationOrchestrator
import com.example.regresoacasa.core.recovery.RecoveryManager
import com.example.regresoacasa.core.security.SecurityManager
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.model.Ruta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SafeReturnEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val backendUrl: String
) {
    
    private val locationOrchestrator = LocationOrchestrator(context)
    private val emergencyManager = EmergencyManager(context, scope, backendUrl)
    private val recoveryManager = RecoveryManager(context)
    private val securityManager = SecurityManager(context)
    
    private val _state = MutableStateFlow<SafeReturnState>(SafeReturnState.Idle)
    val state: StateFlow<SafeReturnState> = _state.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Online)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    private val _gpsStatus = locationOrchestrator.gpsStatus
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()
    
    private var trackingJob: Job? = null
    private var snapshotJob: Job? = null
    
    private var emergencyContacts: List<String> = emptyList()
    private var currentRoute: Ruta? = null
    
    init {
        scope.launch {
            val restoredState = recoveryManager.restore()
            if (restoredState != null) {
                _state.value = restoredState
                if (restoredState is SafeReturnState.Navigating) {
                    startTracking(restoredState.currentLocation)
                }
            }
        }
        
        startSnapshotLoop()
    }
    
    fun setEmergencyContacts(contacts: List<String>) {
        emergencyContacts = contacts
    }
    
    fun startNavigation(destination: String, route: Ruta, startLocation: UbicacionUsuario) {
        scope.launch {
            _state.value = SafeReturnState.Preparing
            
            currentRoute = route
            
            _state.value = SafeReturnState.Navigating(
                destination = destination,
                route = route,
                currentLocation = startLocation,
                startTime = System.currentTimeMillis(),
                remainingDistance = route.distanciaMetros,
                eta = calculateETA(route.duracionSegundos)
            )
            
            startTracking(startLocation)
        }
    }
    
    fun triggerEmergency(reason: String) {
        scope.launch {
            val currentState = _state.value
            val location = when (currentState) {
                is SafeReturnState.Navigating -> currentState.currentLocation
                else -> null
            }
            
            _state.value = SafeReturnState.Emergency(
                reason = reason,
                timestamp = System.currentTimeMillis(),
                deliveryStatus = EmergencyDeliveryStatus.Sending,
                lastLocation = location
            )
            
            emergencyManager.triggerEmergency(
                reason = reason,
                contacts = emergencyContacts,
                location = location,
                batteryLevel = getBatteryLevel()
            )
            
            // Observe delivery status
            emergencyManager.deliveryStatus.collect { status ->
                val current = _state.value
                if (current is SafeReturnState.Emergency) {
                    _state.value = current.copy(deliveryStatus = status)
                }
            }
        }
    }
    
    fun stopNavigation() {
        trackingJob?.cancel()
        locationOrchestrator.stopTracking()
        _state.value = SafeReturnState.Idle
        currentRoute = null
    }
    
    fun markAsArrived() {
        val currentState = _state.value
        if (currentState is SafeReturnState.Navigating) {
            val duration = System.currentTimeMillis() - currentState.startTime
            _state.value = SafeReturnState.Arrived(
                destination = currentState.destination,
                duration = duration,
                distance = currentState.route.distanciaMetros
            )
            stopNavigation()
        }
    }
    
    private fun startTracking(initialLocation: UbicacionUsuario) {
        trackingJob?.cancel()
        
        trackingJob = scope.launch {
            locationOrchestrator.startTracking().collect { location ->
                withContext(Dispatchers.Default) {
                    updateLocation(location)
                }
            }
        }
    }
    
    private suspend fun updateLocation(location: android.location.Location) {
        val ubicacion = UbicacionUsuario(location.latitude, location.longitude)
        
        val currentState = _state.value
        if (currentState is SafeReturnState.Navigating) {
            val newDistance = calculateRemainingDistance(ubicacion, currentState.route)
            val newETA = calculateETA(newDistance / currentState.route.distanciaMetros * currentState.route.duracionSegundos)
            
            _state.value = currentState.copy(
                currentLocation = ubicacion,
                remainingDistance = newDistance,
                eta = newETA
            )
            
            // Check if arrived
            if (newDistance < 20) { // 20 meters threshold
                markAsArrived()
            }
        }
    }
    
    private fun startSnapshotLoop() {
        snapshotJob = scope.launch {
            while (true) {
                delay(30000) // Every 30 seconds
                recoveryManager.saveSnapshot(_state.value)
            }
        }
    }
    
    private fun calculateRemainingDistance(location: UbicacionUsuario, route: Ruta): Double {
        if (route.puntos.isEmpty()) return route.distanciaMetros
        
        var minDistance = Double.MAX_VALUE
        for (punto in route.puntos) {
            val dist = haversineDistance(
                location.latitud, location.longitud,
                punto.latitud, punto.longitud
            )
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }
    
    private fun calculateETA(durationSeconds: Double): String {
        val minutes = (durationSeconds / 60).toInt()
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.MINUTE, minutes)
        val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return format.format(calendar.time)
    }
    
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
    
    private fun getBatteryLevel(): Int {
        // Implement battery level retrieval
        return 100 // Placeholder
    }
    
    fun cleanup() {
        trackingJob?.cancel()
        snapshotJob?.cancel()
        locationOrchestrator.stopTracking()
        emergencyManager.clear()
    }
}
