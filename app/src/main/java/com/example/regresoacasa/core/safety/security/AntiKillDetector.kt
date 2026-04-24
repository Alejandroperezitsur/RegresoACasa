package com.example.regresoacasa.core.safety.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * V3 FASE 4 — ANTI-KILL DETECTION
 * 
 * Detecta cuando la app fue terminada inesperadamente (kill inesperado).
 * Si el usuario hace swipe kill o fuerza el cierre, se detecta al reiniciar.
 */
class AntiKillDetector(
    private val context: Context,
    private val scope: CoroutineScope
) {
    
    private val dataStore: DataStore<Preferences> by preferencesDataStore(name = "anti_kill")
    
    private val WAS_RUNNING_KEY = booleanPreferencesKey("was_running")
    private val LAST_SHUTDOWN_CLEAN_KEY = booleanPreferencesKey("last_shutdown_clean")
    private val LAST_START_TIME_KEY = longPreferencesKey("last_start_time")
    private val LAST_SHUTDOWN_TIME_KEY = longPreferencesKey("last_shutdown_time")
    
    // Callback cuando se detecta kill inesperado
    var onUnexpectedKill: ((Long) -> Unit)? = null
    
    /**
     * Marca que la app está corriendo
     * Debe llamarse al iniciar la app
     */
    fun markRunning() {
        scope.launch(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences[WAS_RUNNING_KEY] = true
                preferences[LAST_SHUTDOWN_CLEAN_KEY] = false
                preferences[LAST_START_TIME_KEY] = System.currentTimeMillis()
            }
        }
    }
    
    /**
     * Marca shutdown limpio
     * Debe llamarse al cerrar la app correctamente
     */
    fun markCleanShutdown() {
        scope.launch(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences[WAS_RUNNING_KEY] = false
                preferences[LAST_SHUTDOWN_CLEAN_KEY] = true
                preferences[LAST_SHUTDOWN_TIME_KEY] = System.currentTimeMillis()
            }
        }
    }
    
    /**
     * Verifica si hubo un kill inesperado
     * Debe llamarse al iniciar la app
     */
    fun checkUnexpectedKill() {
        scope.launch(Dispatchers.IO) {
            val preferences = dataStore.data.first()
            val wasRunning = preferences[WAS_RUNNING_KEY] ?: false
            val lastShutdownClean = preferences[LAST_SHUTDOWN_CLEAN_KEY] ?: true
            val lastStartTime = preferences[LAST_START_TIME_KEY] ?: 0L
            
            // Si estaba corriendo y el shutdown no fue limpio → kill inesperado
            if (wasRunning && !lastShutdownClean) {
                val timeSinceStart = System.currentTimeMillis() - lastStartTime
                onUnexpectedKill?.invoke(timeSinceStart)
                
                // Limpiar flags
                dataStore.edit { prefs ->
                    prefs[WAS_RUNNING_KEY] = false
                    prefs[LAST_SHUTDOWN_CLEAN_KEY] = true
                }
            }
        }
    }
    
    /**
     * Obtiene el estado actual
     */
    suspend fun getState(): KillState {
        val preferences = dataStore.data.first()
        return KillState(
            wasRunning = preferences[WAS_RUNNING_KEY] ?: false,
            lastShutdownClean = preferences[LAST_SHUTDOWN_CLEAN_KEY] ?: true,
            lastStartTime = preferences[LAST_START_TIME_KEY] ?: 0L,
            lastShutdownTime = preferences[LAST_SHUTDOWN_TIME_KEY] ?: 0L
        )
    }
    
    /**
     * Verifica si el shutdown anterior fue limpio
     */
    suspend fun wasLastShutdownClean(): Boolean {
        val preferences = dataStore.data.first()
        return preferences[LAST_SHUTDOWN_CLEAN_KEY] ?: true
    }
    
    /**
     * Limpia el estado
     */
    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    /**
     * Estado de kill
     */
    data class KillState(
        val wasRunning: Boolean,
        val lastShutdownClean: Boolean,
        val lastStartTime: Long,
        val lastShutdownTime: Long
    ) {
        /**
         * Verifica si hubo un kill inesperado
         */
        fun wasUnexpectedKill(): Boolean = wasRunning && !lastShutdownClean
        
        /**
         * Obtiene el tiempo de ejecución antes del kill
         */
        fun getRuntimeBeforeKill(): Long {
            if (!wasUnexpectedKill()) return 0L
            return if (lastShutdownTime > 0) {
                lastShutdownTime - lastStartTime
            } else {
                System.currentTimeMillis() - lastStartTime
            }
        }
    }
}
