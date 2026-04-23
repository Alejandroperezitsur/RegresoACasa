package com.example.regresoacasa.data.safety

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.regresoacasa.MainActivity
import com.example.regresoacasa.R
import com.example.regresoacasa.domain.model.UbicacionUsuario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SafetyForegroundService - Servicio en foreground para monitoreo de seguridad
 * Mantiene el monitoreo activo incluso cuando la app está en background
 */
class SafetyForegroundService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitoringJob: Job? = null
    
    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState = _serviceState.asStateFlow()
    
    private lateinit var tripMonitorEngine: TripMonitorEngine
    private lateinit var safetyAlertEngine: SafetyAlertEngine
    private lateinit var liveTrackingSession: LiveTrackingSession
    
    private var currentTripId: String? = null
    private var expectedArrivalTime: Long = 0
    private var emergencyContacts: List<EmergencyContact> = emptyList()
    
    companion object {
        private const val CHANNEL_ID = "safety_monitoring"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_START_MONITORING = "com.example.regresoacasa.START_MONITORING"
        private const val ACTION_STOP_MONITORING = "com.example.regresoacasa.STOP_MONITORING"
        private const val ACTION_EMERGENCY = "com.example.regresoacasa.EMERGENCY"
        
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_ETA_MINUTES = "eta_minutes"
        const val EXTRA_CONTACTS = "contacts"
        
        fun startService(
            context: Context,
            tripId: String,
            destination: String,
            etaMinutes: Int,
            contacts: List<EmergencyContact>
        ) {
            val intent = Intent(context, SafetyForegroundService::class.java).apply {
                action = ACTION_START_MONITORING
                putExtra(EXTRA_TRIP_ID, tripId)
                putExtra(EXTRA_DESTINATION, destination)
                putExtra(EXTRA_ETA_MINUTES, etaMinutes)
                putStringArrayListExtra(EXTRA_CONTACTS, ArrayList(contacts.map { "${it.id}|${it.name}|${it.phoneNumber}|${it.relationship}|${it.isPrimary}" }))
            }
            context.startForegroundService(intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, SafetyForegroundService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
        
        fun triggerEmergency(context: Context) {
            val intent = Intent(context, SafetyForegroundService::class.java).apply {
                action = ACTION_EMERGENCY
            }
            context.startService(intent)
        }
    }
    
    data class ServiceState(
        val isMonitoring: Boolean = false,
        val tripId: String? = null,
        val currentLocation: UbicacionUsuario? = null,
        val alertLevel: AlertLevel = AlertLevel.NONE,
        val monitoringStartTime: Long = 0
    )
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        tripMonitorEngine = TripMonitorEngine(serviceScope)
        safetyAlertEngine = SafetyAlertEngine(this, serviceScope)
        liveTrackingSession = LiveTrackingSession(this, serviceScope)
        
        Log.d("SafetyForegroundService", "Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: return START_NOT_STICKY
                val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: return START_NOT_STICKY
                val etaMinutes = intent.getIntExtra(EXTRA_ETA_MINUTES, 0)
                val contactsData = intent.getStringArrayListExtra(EXTRA_CONTACTS) ?: emptyList()
                
                emergencyContacts = contactsData.map { data ->
                    val parts = data.split("|")
                    EmergencyContact(
                        id = parts[0].toLongOrNull() ?: 0,
                        name = parts.getOrElse(1) { "" },
                        phoneNumber = parts.getOrElse(2) { "" },
                        relationship = parts.getOrElse(3) { "" },
                        isPrimary = parts.getOrElse(4) { "false" }.toBoolean()
                    )
                }
                
                startMonitoring(tripId, destination, etaMinutes)
            }
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
            }
            ACTION_EMERGENCY -> {
                triggerEmergency()
            }
        }
        
        return START_STICKY
    }
    
    private fun startMonitoring(tripId: String, destination: String, etaMinutes: Int) {
        currentTripId = tripId
        expectedArrivalTime = System.currentTimeMillis() + (etaMinutes * 60 * 1000L)
        
        _serviceState.value = ServiceState(
            isMonitoring = true,
            tripId = tripId,
            monitoringStartTime = System.currentTimeMillis()
        )
        
        startForeground(NOTIFICATION_ID, createMonitoringNotification(destination, etaMinutes))
        
        tripMonitorEngine.startMonitoring(expectedArrivalTime)
        
        val shareableLink = liveTrackingSession.startSession(destination, etaMinutes, emergencyContacts)
        
        monitoringJob = serviceScope.launch {
            tripMonitorEngine.monitorState.collect { monitorState ->
                val alertLevel = tripMonitorEngine.getAlertLevel()
                
                _serviceState.value = _serviceState.value.copy(
                    alertLevel = alertLevel,
                    currentLocation = null // Will be updated via location updates
                )
                
                safetyAlertEngine.processAlertLevelChange(
                    alertLevel,
                    _serviceState.value.currentLocation,
                    emergencyContacts
                )
                
                if (alertLevel == AlertLevel.CRITICAL) {
                    liveTrackingSession.updateStatus(LiveTrackingSession.TrackingStatus.ALERT)
                }
            }
        }
        
        Log.d("SafetyForegroundService", "Monitoring started for trip: $tripId")
    }
    
    private fun stopMonitoring() {
        monitoringJob?.cancel()
        tripMonitorEngine.stopMonitoring()
        liveTrackingSession.stopSession(emergencyContacts)
        safetyAlertEngine.clearAllAlerts()
        
        _serviceState.value = ServiceState(isMonitoring = false)
        
        stopForeground(true)
        stopSelf()
        
        Log.d("SafetyForegroundService", "Monitoring stopped")
    }
    
    private fun triggerEmergency() {
        val location = _serviceState.value.currentLocation
        
        safetyAlertEngine.processAlertLevelChange(
            AlertLevel.CRITICAL,
            location,
            emergencyContacts
        )
        
        liveTrackingSession.updateStatus(LiveTrackingSession.TrackingStatus.ALERT)
        
        Log.d("SafetyForegroundService", "Emergency triggered")
    }
    
    fun updateLocation(location: UbicacionUsuario, route: com.example.regresoacasa.domain.model.Ruta?, speedKmh: Double) {
        _serviceState.value = _serviceState.value.copy(currentLocation = location)
        
        tripMonitorEngine.processLocationUpdate(location, route, speedKmh)
        liveTrackingSession.updateLocation(location)
        
        // Check for automatic check-in (arrival)
        val destination = _serviceState.value.tripId?.let { 
            // In a real implementation, we'd get the destination from the trip
            null 
        }
        
        if (destination != null) {
            val distanceToDestination = calculateDistanceToDestination(location, destination)
            if (distanceToDestination < 30) { // 30 meters threshold
                completeTrip()
            }
        }
    }
    
    private fun calculateDistanceToDestination(location: UbicacionUsuario, destination: String): Double {
        // In a real implementation, we'd calculate actual distance to destination
        // For now, return a large value
        return Double.MAX_VALUE
    }
    
    private fun completeTrip() {
        liveTrackingSession.updateStatus(LiveTrackingSession.TrackingStatus.ARRIVED)
        stopMonitoring()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitoreo de Seguridad",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Monitoreo activo durante el viaje"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createMonitoringNotification(destination: String, etaMinutes: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Regreso Seguro Activo")
            .setContentText("Monitoreando tu viaje a $destination")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        monitoringJob?.cancel()
        serviceScope.cancel()
        Log.d("SafetyForegroundService", "Service destroyed")
    }
}
