package com.example.regresoacasa.core.safety.heartbeat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Extension property at file level
private val Context.heartbeatDataStore: DataStore<Preferences> by preferencesDataStore(name = "heartbeat")

/**
 * V3 FASE 3 — HEARTBEAT GLOBAL PERSISTENTE
 * 
 * Sistema de heartbeat que verifica que el sistema sigue vivo.
 * Si el heartbeat se detiene, el watchdog detecta que el sistema está muerto.
 */
class HeartbeatManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    
    private val dataStore: DataStore<Preferences> = context.heartbeatDataStore
    
    private val HEARTBEAT_KEY = longPreferencesKey("last_heartbeat")
    private val HEARTBEAT_INTERVAL_KEY = longPreferencesKey("heartbeat_interval")
    
    private val _isBeating = MutableStateFlow(false)
    val isBeating: StateFlow<Boolean> = _isBeating.asStateFlow()
    
    private val _lastHeartbeat = MutableStateFlow(0L)
    val lastHeartbeat: StateFlow<Long> = _lastHeartbeat.asStateFlow()
    
    private var heartbeatJob: Job? = null
    private var isRunning = false
    
    // Intervalo de heartbeat (default: 30 segundos)
    private var heartbeatInterval = 30000L
    
    // Callback cuando se detecta que el sistema está muerto
    var onSystemDead: ((Long) -> Unit)? = null
    
    /**
     * Inicia el heartbeat
     */
    fun start(intervalMs: Long = 30000L) {
        if (isRunning) {
            return
        }
        
        heartbeatInterval = intervalMs
        isRunning = true
        _isBeating.value = true
        
        // Guardar intervalo
        scope.launch(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences[HEARTBEAT_INTERVAL_KEY] = heartbeatInterval
            }
        }
        
        // Iniciar loop de heartbeat
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isRunning) {
                beat()
                delay(heartbeatInterval)
            }
        }
        
        // Verificar heartbeat previo al iniciar
        scope.launch(Dispatchers.IO) {
            checkPreviousHeartbeat()
        }
    }
    
    /**
     * Detiene el heartbeat
     */
    fun stop() {
        isRunning = false
        heartbeatJob?.cancel()
        _isBeating.value = false
        
        // Marcar shutdown limpio
        scope.launch(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences[HEARTBEAT_KEY] = -1L // -1 indica shutdown limpio
            }
        }
    }
    
    /**
     * Genera un beat
     */
    fun beat() {
        val now = System.currentTimeMillis()
        _lastHeartbeat.value = now
        
        scope.launch(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences[HEARTBEAT_KEY] = now
            }
        }
    }
    
    /**
     * Verifica el heartbeat previo al iniciar
     */
    private suspend fun checkPreviousHeartbeat() {
        val preferences = dataStore.data.first()
        val lastBeat = preferences[HEARTBEAT_KEY] ?: 0L
        val interval = preferences[HEARTBEAT_INTERVAL_KEY] ?: heartbeatInterval
        
        // Si es -1, fue un shutdown limpio
        if (lastBeat == -1L) {
            return
        }
        
        // Si no hay heartbeat previo, es primera ejecución
        if (lastBeat == 0L) {
            return
        }
        
        val now = System.currentTimeMillis()
        val timeSinceLastBeat = now - lastBeat
        
        // Si el tiempo desde el último beat es > 3x el intervalo, el sistema murió
        val threshold = interval * 3
        if (timeSinceLastBeat > threshold) {
            onSystemDead?.invoke(timeSinceLastBeat)
        }
    }
    
    /**
     * Obtiene el último heartbeat persistido
     */
    suspend fun getLastPersistedHeartbeat(): Long {
        val preferences = dataStore.data.first()
        return preferences[HEARTBEAT_KEY] ?: 0L
    }
    
    /**
     * Verifica si el sistema está vivo
     */
    suspend fun isSystemAlive(): Boolean {
        val lastBeat = getLastPersistedHeartbeat()
        
        // -1 indica shutdown limpio
        if (lastBeat == -1L) {
            return true
        }
        
        val now = System.currentTimeMillis()
        val interval = dataStore.data.first()[HEARTBEAT_INTERVAL_KEY] ?: heartbeatInterval
        val threshold = interval * 3
        
        return (now - lastBeat) < threshold
    }
    
    /**
     * Obtiene el tiempo desde el último heartbeat
     */
    suspend fun getTimeSinceLastHeartbeat(): Long {
        val lastBeat = getLastPersistedHeartbeat()
        if (lastBeat == -1L || lastBeat == 0L) {
            return 0L
        }
        
        return System.currentTimeMillis() - lastBeat
    }
    
    /**
     * Limpia el heartbeat
     */
    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
