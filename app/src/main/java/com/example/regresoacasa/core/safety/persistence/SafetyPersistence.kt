package com.example.regresoacasa.core.safety.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.regresoacasa.core.safety.SafetyConstants
import com.example.regresoacasa.core.safety.alert.AlertPersistence
import com.example.regresoacasa.core.safety.alert.AlertStatus
import com.example.regresoacasa.core.safety.alert.PendingAlert
import com.example.regresoacasa.core.safety.state.LocationSnapshot
import com.example.regresoacasa.core.safety.state.SafetyMode
import com.example.regresoacasa.core.safety.state.SafetySnapshot
import com.example.regresoacasa.core.safety.state.SafetyState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// Extension property at file level
private val Context.safetyPersistenceDataStore: DataStore<Preferences> by preferencesDataStore(name = "safety_state")

/**
 * Persistencia de Seguridad Real
 * 
 * Sistema de persistencia fault-tolerant:
 * - Guarda snapshots del estado
 * - Persiste alertas pendientes
 * - Sobrevive a process death
 * - Recupera estado automáticamente
 */
class SafetyPersistence(private val context: Context) : AlertPersistence {
    
    private val dataStore: DataStore<Preferences> = context.safetyPersistenceDataStore
    
    // Keys para DataStore
    private val STATE_KEY = stringPreferencesKey("safety_state")
    private val MODE_KEY = stringPreferencesKey("safety_mode")
    private val LAST_UPDATE_KEY = longPreferencesKey("last_update")
    private val LOCATION_KEY = stringPreferencesKey("last_location")
    private val BATTERY_KEY = stringPreferencesKey("battery_level")
    private val HAS_INTERNET_KEY = stringPreferencesKey("has_internet")
    
    // Keys para alertas pendientes
    private val PENDING_ALERTS_KEY = stringPreferencesKey("pending_alerts")
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Guarda un snapshot del estado del sistema
     */
    suspend fun saveSnapshot(snapshot: SafetySnapshot) {
        dataStore.edit { preferences ->
            preferences[STATE_KEY] = json.encodeToString(snapshot.state)
            preferences[MODE_KEY] = snapshot.mode.name
            preferences[LAST_UPDATE_KEY] = snapshot.lastUpdate
            preferences[BATTERY_KEY] = snapshot.batteryLevel.toString()
            preferences[HAS_INTERNET_KEY] = snapshot.hasInternet.toString()
            
            snapshot.lastLocation?.let { location ->
                preferences[LOCATION_KEY] = json.encodeToString(location)
            } ?: run {
                preferences[LOCATION_KEY] = ""
            }
        }
    }
    
    /**
     * Carga el último snapshot del estado
     */
    suspend fun loadSnapshot(): SafetySnapshot? {
        try {
            val preferences = dataStore.data.first()
            
            val stateJson = preferences[STATE_KEY] ?: return null
            val modeName = preferences[MODE_KEY] ?: return null
            val lastUpdate = preferences[LAST_UPDATE_KEY] ?: return null
            val batteryLevel = preferences[BATTERY_KEY]?.toIntOrNull() ?: 0
            val hasInternet = preferences[HAS_INTERNET_KEY]?.toBoolean() ?: false
            val locationJson = preferences[LOCATION_KEY]
            
            val state = json.decodeFromString<SafetyState>(stateJson)
            val mode = SafetyMode.valueOf(modeName)
            val location = if (locationJson.isNullOrEmpty()) {
                null
            } else {
                json.decodeFromString<LocationSnapshot>(locationJson)
            }
            
            val snapshot = SafetySnapshot(
                state = state,
                mode = mode,
                lastLocation = location,
                lastUpdate = lastUpdate,
                batteryLevel = batteryLevel,
                hasInternet = hasInternet
            )
            
            // Verificar si el snapshot es válido (no muy viejo)
            if (!snapshot.isValid()) {
                clearSnapshot()
                return null
            }
            
            return snapshot
        } catch (e: Exception) {
            // Error al cargar, limpiar y retornar null
            clearSnapshot()
            return null
        }
    }
    
    /**
     * Limpia el snapshot guardado
     */
    suspend fun clearSnapshot() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    // ==================== ALERT PERSISTENCE ====================
    
    /**
     * Guarda una alerta pendiente
     */
    override suspend fun savePendingAlert(alert: PendingAlert) {
        val currentAlerts = getPendingAlerts().toMutableList()
        currentAlerts.add(alert)
        
        val alertsJson = json.encodeToString(currentAlerts)
        dataStore.edit { preferences ->
            preferences[PENDING_ALERTS_KEY] = alertsJson
        }
    }
    
    /**
     * Obtiene una alerta pendiente por ID
     */
    override suspend fun getPendingAlert(id: String): PendingAlert? {
        return getPendingAlerts().find { it.id == id }
    }
    
    /**
     * Obtiene todas las alertas pendientes
     */
    override suspend fun getPendingAlerts(): List<PendingAlert> {
        try {
            val preferences = dataStore.data.first()
            val alertsJson = preferences[PENDING_ALERTS_KEY] ?: return emptyList()
            
            if (alertsJson.isEmpty()) return emptyList()
            
            return json.decodeFromString<List<PendingAlert>>(alertsJson)
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    /**
     * Actualiza una alerta pendiente
     */
    override suspend fun updatePendingAlert(alert: PendingAlert) {
        val currentAlerts = getPendingAlerts().toMutableList()
        val index = currentAlerts.indexOfFirst { it.id == alert.id }
        
        if (index >= 0) {
            currentAlerts[index] = alert
            
            val alertsJson = json.encodeToString(currentAlerts)
            dataStore.edit { preferences ->
                preferences[PENDING_ALERTS_KEY] = alertsJson
            }
        }
    }
    
    /**
     * Elimina una alerta pendiente
     */
    override suspend fun deletePendingAlert(id: String) {
        val currentAlerts = getPendingAlerts().toMutableList()
        currentAlerts.removeAll { it.id == id }
        
        val alertsJson = json.encodeToString(currentAlerts)
        dataStore.edit { preferences ->
            preferences[PENDING_ALERTS_KEY] = alertsJson
        }
    }
    
    /**
     * Obtiene alertas con delivery incierto
     */
    override suspend fun getUncertainDeliveries(threshold: Long): List<PendingAlert> {
        return getPendingAlerts().filter { alert ->
            alert.status == AlertStatus.SENT && 
            alert.timestamp < threshold
        }
    }
}
