package com.example.regresoacasa.data.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.regresoacasa.MainActivity
import com.example.regresoacasa.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Foreground Service para tracking de ubicación en background.
 * Requerido para Android 10+ cuando la app está en background.
 */
class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var isTracking = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val interval = intent.getLongExtra(EXTRA_INTERVAL, 3000L)
                startTracking(interval)
            }
            ACTION_STOP_TRACKING -> {
                stopTracking()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTracking(intervalMillis: Long) {
        if (isTracking) return
        
        isTracking = true
        
        // Crear notificación persistente
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Configurar location request
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMillis
        ).apply {
            setMinUpdateIntervalMillis(intervalMillis / 2)
            setWaitForAccurateLocation(true)
        }.build()
        
        // Callback de ubicación
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")
                    // Aquí se pueden enviar actualizaciones via broadcast o guardar en BD
                    sendLocationBroadcast(location.latitude, location.longitude, location.accuracy)
                }
            }
        }
        
        locationCallback = callback
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location tracking started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting location updates", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
            stopSelf()
        }
    }

    private fun stopTracking() {
        if (!isTracking) return
        
        isTracking = false
        
        locationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing location updates", e)
            }
            locationCallback = null
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "Location tracking stopped")
    }

    private fun sendLocationBroadcast(lat: Double, lon: Double, accuracy: Float) {
        val intent = Intent(ACTION_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LATITUDE, lat)
            putExtra(EXTRA_LONGITUDE, lon)
            putExtra(EXTRA_ACCURACY, accuracy)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Navegación en curso",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el estado de navegación activa"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Navegando a casa")
            .setContentText("RegresoACasa está siguiendo tu ruta")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
    }

    companion object {
        private const val TAG = "LocationForegroundSvc"
        private const val CHANNEL_ID = "navigation_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_TRACKING = "com.example.regresoacasa.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.regresoacasa.ACTION_STOP_TRACKING"
        const val ACTION_LOCATION_UPDATE = "com.example.regresoacasa.ACTION_LOCATION_UPDATE"
        
        const val EXTRA_INTERVAL = "extra_interval"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_ACCURACY = "extra_accuracy"
        
        fun start(context: Context, intervalMillis: Long = 3000L) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_INTERVAL, intervalMillis)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }
    }
}
