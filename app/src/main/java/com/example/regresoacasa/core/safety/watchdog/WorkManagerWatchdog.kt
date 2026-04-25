package com.example.regresoacasa.core.safety.watchdog

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// Extension property para DataStore - requiere Context
private val Context.watchdogDataStore: DataStore<Preferences> by preferencesDataStore(name = "watchdog")

/**
 * V3 FASE 8 — AISLAMIENTO DE PROCESOS (WORKMANAGER WATCHDOG)
 * 
 * Watchdog que corre en WorkManager, independiente del proceso principal.
 * Si el proceso principal es matado, este watchdog sigue funcionando.
 */
class WorkManagerWatchdog(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    
    private val dataStore: DataStore<Preferences> = applicationContext.watchdogDataStore
    private val LAST_WATCHDOG_CHECK_KEY = longPreferencesKey("last_watchdog_check")
    private val HEARTBEAT_KEY = longPreferencesKey("last_heartbeat")
    
    override suspend fun doWork(): Result {
        // Verificar heartbeat del sistema principal
        val lastHeartbeat = getLastHeartbeat()
        val now = System.currentTimeMillis()
        val timeSinceHeartbeat = now - lastHeartbeat
        
        // Si el heartbeat es muy viejo (> 90 segundos), el sistema está muerto
        if (lastHeartbeat > 0 && timeSinceHeartbeat > 90000) {
            // Sistema muerto - podría disparar alerta
            // Por ahora, solo loggear
            android.util.Log.e("WorkManagerWatchdog", "System dead - no heartbeat for ${timeSinceHeartbeat}ms")
            
            // Guardar timestamp de detección
            saveLastWatchdogCheck(now)
            
            // Retornar success para que el trabajo continúe
            return Result.success()
        }
        
        // Guardar timestamp de verificación
        saveLastWatchdogCheck(now)
        
        return Result.success()
    }
    
    private suspend fun getLastHeartbeat(): Long {
        val preferences = dataStore.data.first()
        return preferences[HEARTBEAT_KEY] ?: 0L
    }
    
    private suspend fun saveLastWatchdogCheck(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_WATCHDOG_CHECK_KEY] = timestamp
        }
    }
    
    companion object {
        /**
         * Programa el watchdog en WorkManager
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<WorkManagerWatchdog>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "safety_watchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
        
        /**
         * Cancela el watchdog
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("safety_watchdog")
        }
    }
}
