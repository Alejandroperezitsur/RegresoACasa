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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.regresoacasa.MainActivity
import com.example.regresoacasa.R
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.core.safety.watchdog.SafetyWatchdog
import com.example.regresoacasa.data.safety.ReliableAlertDispatcher
import com.example.regresoacasa.data.local.PreferencesManager
import com.example.regresoacasa.data.safety.BatteryOptimizationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SafetyForegroundService : Service() {
    
    companion object {
        private const val TAG = "SafetyForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "safety_monitoring_channel"
        private const val ACTION_START = "com.example.regresoacasa.ACTION_START_SAFETY_SERVICE"
        private const val ACTION_STOP = "com.example.regresoacasa.ACTION_STOP_SAFETY_SERVICE"
        private const val ACTION_CRITICAL_ALERT = "com.example.regresoacasa.ACTION_CRITICAL_ALERT"
        private const val ACTION_RESTART_SERVICE = "com.example.regresoacasa.ACTION_RESTART_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, SafetyForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SafetyForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var safetyWatchdog: SafetyWatchdog? = null
    private var batteryOptimizationHelper: BatteryOptimizationHelper? = null
    private var preferencesManager: PreferencesManager? = null
    private var alertDispatcher: ReliableAlertDispatcher? = null
    private var isMonitoring = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SafetyForegroundService created")
        
        preferencesManager = PreferencesManager(this)
        batteryOptimizationHelper = BatteryOptimizationHelper(this)
        
        createNotificationChannel()
        
        safetyWatchdog = SafetyWatchdog(
            this,
            preferencesManager!!,
            serviceScope
        )
        
        alertDispatcher = ReliableAlertDispatcher(
            this,
            (application as com.example.regresoacasa.RegresoACasaApp).database.alertDeliveryDao(),
            serviceScope
        )
        
        alertDispatcher?.registerReceivers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                startMonitoring()
            }
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
            }
            ACTION_CRITICAL_ALERT -> {
                handleCriticalAlert(intent)
            }
            ACTION_RESTART_SERVICE -> {
                Log.d(TAG, "Service restart requested - rehydrating from DB")
                rehydrateFromDb()
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SafetyForegroundService destroyed")
        
        stopMonitoring()
        alertDispatcher?.unregisterReceivers()
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed - service will restart due to START_STICKY")
    }

    private fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring")
            return
        }

        isMonitoring = true
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.w(TAG, "WARNING: App is subject to battery optimization")
            }
        }

        safetyWatchdog?.start()
        
        startForeground(NOTIFICATION_ID, createMonitoringNotification())
        
        Log.d(TAG, "Safety monitoring started")
    }

    private fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        isMonitoring = false
        safetyWatchdog?.stop()
        
        Log.d(TAG, "Safety monitoring stopped")
    }

    private fun handleCriticalAlert(intent: Intent) {
        val reason = intent.getStringExtra("reason") ?: "unknown"
        val lastGpsUpdate = intent.getLongExtra("last_gps_update", 0L)
        val lastMonitorCycle = intent.getLongExtra("last_monitor_cycle", 0L)
        
        Log.e(TAG, "CRITICAL ALERT: $reason - GPS: $lastGpsUpdate, Cycle: $lastMonitorCycle")
        
        serviceScope.launch {
            try {
                alertDispatcher?.retryPendingAlerts()
            } catch (e: Exception) {
                Log.e(TAG, "Error retrying alerts on critical", e)
            }
        }
        
        safetyWatchdog?.attemptServiceRestart()
    }

    private fun rehydrateFromDb() {
        Log.d(TAG, "Rehydrating service state from database")
        
        serviceScope.launch {
            try {
                val pendingAlerts = (application as com.example.regresoacasa.RegresoACasaApp)
                    .database.alertDeliveryDao().getPendingAlerts()
                
                if (pendingAlerts.isNotEmpty()) {
                    Log.d(TAG, "Found ${pendingAlerts.size} pending alerts, retrying")
                    alertDispatcher?.retryPendingAlerts()
                }
                
                if (!isMonitoring) {
                    startMonitoring()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rehydrating from DB", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Safety Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors your safety during trips"
                setShowBadge(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createMonitoringNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Regreso Seguro Activo")
            .setContentText("Monitoreando tu seguridad en tiempo real")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun updateGpsTimestamp(timestamp: Long) {
        safetyWatchdog?.updateGpsTimestamp(timestamp)
    }

    fun updateMonitorCycleTimestamp(timestamp: Long) {
        safetyWatchdog?.updateMonitorCycleTimestamp(timestamp)
    }
}
