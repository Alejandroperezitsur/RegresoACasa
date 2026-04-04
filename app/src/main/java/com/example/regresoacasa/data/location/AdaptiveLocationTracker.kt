package com.example.regresoacasa.data.location

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.google.android.gms.location.Priority

/**
 * Tracker de ubicación adaptativo basado en velocidad y batería
 */
class AdaptiveLocationTracker(private val context: Context) {

    /**
     * Calcula el intervalo de actualización basado en velocidad
     */
    fun getUpdateInterval(velocidadMps: Double): Long {
        return when {
            velocidadMps < 0.5 -> 5000L      // Detenido: 5s
            velocidadMps < 2.0 -> 3000L      // Caminando lento: 3s
            velocidadMps < 5.0 -> 2000L      // Caminando rápido: 2s
            velocidadMps < 15.0 -> 1000L     // Trotando/corriendo: 1s
            else -> 1000L                     // Vehículo: 1s
        }
    }
    
    /**
     * Obtiene la prioridad basada en nivel de batería
     */
    fun getPriority(): Int {
        val batteryLevel = getBatteryLevel()
        return if (batteryLevel < 0.2) {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY  // Batería baja
        } else {
            Priority.PRIORITY_HIGH_ACCURACY            // Normal
        }
    }
    
    /**
     * Obtiene el nivel de batería actual (0.0 a 1.0)
     */
    private fun getBatteryLevel(): Float {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        
        return if (level >= 0 && scale > 0) {
            level / scale.toFloat()
        } else {
            1.0f // Asume batería completa si no se puede leer
        }
    }
    
    /**
     * Calcula velocidad entre dos puntos (m/s)
     */
    fun calculateVelocity(
        lat1: Double, lon1: Double, time1: Long,
        lat2: Double, lon2: Double, time2: Long
    ): Double {
        val distance = haversineDistance(lat1, lon1, lat2, lon2) // metros
        val timeDiff = (time2 - time1) / 1000.0 // segundos
        
        return if (timeDiff > 0) {
            distance / timeDiff
        } else {
            0.0
        }
    }
    
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000 // Radio de la Tierra en metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}
