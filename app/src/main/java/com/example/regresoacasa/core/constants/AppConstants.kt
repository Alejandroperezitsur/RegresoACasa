package com.example.regresoacasa.core.constants

/**
 * Constants file central - PASO 13
 * Centraliza todas las constantes de la aplicación para evitar duplicación
 * y facilitar mantenimiento.
 */
object AppConstants {
    
    // ============================================
    // NOTIFICATION
    // ============================================
    object Notification {
        const val CHANNEL_ID_SAFETY = "safety_monitoring"
        const val CHANNEL_ID_ALERTS = "safety_alerts"
        const val NOTIFICATION_ID_SAFETY = 1001
        const val NOTIFICATION_ID_ALERT_CRITICAL = 1002
        const val NOTIFICATION_ID_ALERT_COUNTDOWN = 1003
    }
    
    // ============================================
    // BROADCAST ACTIONS
    // ============================================
    object Broadcast {
        const val ACTION_CRITICAL_ALERT = "com.example.regresoacasa.ACTION_CRITICAL_ALERT"
        const val ACTION_RESTART_SERVICE = "com.example.regresoacasa.ACTION_RESTART_SERVICE"
        const val ACTION_SMS_SENT = "com.example.regresoacasa.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.regresoacasa.ACTION_SMS_DELIVERED"
    }
    
    // ============================================
    // BROADCAST EXTRAS
    // ============================================
    object BroadcastExtra {
        const val EXTRA_SMS_ID = "sms_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_REASON = "reason"
    }
    
    // ============================================
    // LOCATION
    // ============================================
    object Location {
        const val ARRIVAL_DISTANCE_METERS = 20.0
        const val LOCATION_HISTORY_MAX_SIZE = 20
        const val SPEED_CHANGE_THRESHOLD_KMH = 3.0f
    }
    
    // ============================================
    // GPS ADAPTIVE INTERVALS (milliseconds)
    // ============================================
    object GpsInterval {
        const val STATIONARY = 10000L  // 10s
        const val WALKING = 5000L     // 5s
        const val VEHICLE = 2000L      // 2s
    }
    
    // ============================================
    // ENCRYPTION
    // ============================================
    object Encryption {
        const val KEY_ALIAS = "SafeReturnMasterKey"
        const val IV_KEY = "encryption_iv"
        const val KEY_SIZE_BITS = 256
    }
    
    // ============================================
    // SNAPSHOT
    // ============================================
    object Snapshot {
        const val SAVE_INTERVAL_MS = 30000L  // 30 seconds
        const val TTL_MS = 300000L  // 5 minutes
    }
    
    // ============================================
    // BATTERY
    // ============================================
    object Battery {
        const val CRITICAL_THRESHOLD = 15
        const val LOW_THRESHOLD = 30
    }
    
    // ============================================
    // SMS
    // ============================================
    object Sms {
        const val MAX_HISTORY_SIZE = 100
        const val TIMEOUT_MS = 60000L  // 60 seconds
    }
    
    // ============================================
    // LIVE TRACKING
    // ============================================
    object LiveTracking {
        const val UPDATE_INTERVAL_MS = 60000L  // 1 minute
    }
    
    // ============================================
    // PERMISSION REQUEST CODES
    // ============================================
    object PermissionRequest {
        const val BATTERY_OPTIMIZATION = 1001
    }
}
