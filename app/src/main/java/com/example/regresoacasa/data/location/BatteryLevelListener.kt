package com.example.regresoacasa.data.location

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Listener de nivel de batería para activación automática de modo ahorro
 */
class BatteryLevelListener(private val context: Context) {
    
    companion object {
        const val LOW_BATTERY_THRESHOLD = 20 // Porcentaje para activar modo ahorro
        const val RESUME_NORMAL_THRESHOLD = 25 // Porcentaje para volver a modo normal
    }
    
    /**
     * Flow que emite el nivel de batería actual (0-100)
     */
    val batteryLevelFlow: Flow<Int> = callbackFlow {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    
                    if (level >= 0 && scale > 0) {
                        val batteryPct = (level * 100 / scale)
                        trySend(batteryPct)
                    }
                }
            }
        }
        
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        
        // Emitir valor inicial
        val batteryStatus = context.registerReceiver(null, filter)
        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                trySend(level * 100 / scale)
            }
        }
        
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }.distinctUntilChanged()
    
    /**
     * Verifica si está en modo ahorro de batería del sistema
     */
    fun isSystemPowerSaveMode(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode ?: false
    }
    
    /**
     * Obtiene el nivel de batería actual (síncrono)
     */
    fun getCurrentBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            100 // Asumir batería completa si no se puede leer
        }
    }
    
    /**
     * Verifica si debe activarse modo ahorro de la app
     */
    fun shouldActivateLowBatteryMode(currentLevel: Int): Boolean {
        return currentLevel <= LOW_BATTERY_THRESHOLD
    }
    
    /**
     * Verifica si debe reanudarse modo normal
     */
    fun shouldResumeNormalMode(currentLevel: Int): Boolean {
        return currentLevel >= RESUME_NORMAL_THRESHOLD
    }
}

/**
 * Estado del modo de batería
 */
sealed class BatteryMode {
    data object Normal : BatteryMode()
    data object LowBattery : BatteryMode()
}
