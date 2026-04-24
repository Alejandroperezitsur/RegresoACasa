package com.example.regresoacasa.core.recovery

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.regresoacasa.core.EmergencyDeliveryStatus
import com.example.regresoacasa.core.SafeReturnState
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.model.Ruta
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.recoveryDataStore: DataStore<Preferences> by preferencesDataStore(name = "safe_return_recovery")

class RecoveryManager(private val context: Context) {
    
    private val dataStore: DataStore<Preferences> = context.recoveryDataStore
    
    private val STATE_KEY = stringPreferencesKey("state")
    private val STATE_TYPE_KEY = stringPreferencesKey("state_type")
    private val LAST_UPDATE_KEY = longPreferencesKey("last_update")
    private val LOCATION_KEY = stringPreferencesKey("location")
    private val ROUTE_KEY = stringPreferencesKey("route")
    private val EMERGENCY_REASON_KEY = stringPreferencesKey("emergency_reason")
    private val EMERGENCY_TIMESTAMP_KEY = longPreferencesKey("emergency_timestamp")
    private val EMERGENCY_STATUS_KEY = stringPreferencesKey("emergency_status")
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    suspend fun saveSnapshot(state: SafeReturnState) {
        dataStore.edit { preferences ->
            preferences[STATE_TYPE_KEY] = state.javaClass.simpleName
            preferences[LAST_UPDATE_KEY] = System.currentTimeMillis()
            
            when (state) {
                is SafeReturnState.Idle -> {
                    preferences[STATE_KEY] = "idle"
                }
                is SafeReturnState.Preparing -> {
                    preferences[STATE_KEY] = "preparing"
                }
                is SafeReturnState.Navigating -> {
                    preferences[STATE_KEY] = "navigating"
                    preferences[LOCATION_KEY] = json.encodeToString(state.currentLocation)
                    preferences[ROUTE_KEY] = json.encodeToString(state.route)
                }
                is SafeReturnState.Emergency -> {
                    preferences[STATE_KEY] = "emergency"
                    preferences[EMERGENCY_REASON_KEY] = state.reason
                    preferences[EMERGENCY_TIMESTAMP_KEY] = state.timestamp
                    preferences[EMERGENCY_STATUS_KEY] = state.deliveryStatus.javaClass.simpleName
                    state.lastLocation?.let {
                        preferences[LOCATION_KEY] = json.encodeToString(it)
                    }
                }
                is SafeReturnState.Arrived -> {
                    preferences[STATE_KEY] = "arrived"
                }
                is SafeReturnState.Failure -> {
                    preferences[STATE_KEY] = "failure"
                }
            }
        }
    }
    
    suspend fun restore(): SafeReturnState? {
        return try {
            val preferences = dataStore.data.first()
            val lastUpdate = preferences[LAST_UPDATE_KEY] ?: return null
            val stateType = preferences[STATE_KEY] ?: return null
            
            // Check if snapshot is too old (older than 24 hours)
            val age = System.currentTimeMillis() - lastUpdate
            if (age > 86400000L) {
                clearSnapshot()
                return null
            }
            
            when (stateType) {
                "idle" -> SafeReturnState.Idle
                "preparing" -> SafeReturnState.Preparing
                "navigating" -> {
                    val locationJson = preferences[LOCATION_KEY] ?: return null
                    val routeJson = preferences[ROUTE_KEY] ?: return null
                    val location = json.decodeFromString<UbicacionUsuario>(locationJson)
                    val route = json.decodeFromString<Ruta>(routeJson)
                    
                    SafeReturnState.Navigating(
                        destination = "Restored",
                        route = route,
                        currentLocation = location,
                        startTime = lastUpdate,
                        remainingDistance = route.distanciaMetros,
                        eta = "Restored"
                    )
                }
                "emergency" -> {
                    val reason = preferences[EMERGENCY_REASON_KEY] ?: "Unknown"
                    val timestamp = preferences[EMERGENCY_TIMESTAMP_KEY] ?: lastUpdate
                    val statusType = preferences[EMERGENCY_STATUS_KEY] ?: "Sending"
                    val locationJson = preferences[LOCATION_KEY]
                    val location = if (locationJson != null) {
                        json.decodeFromString<UbicacionUsuario>(locationJson)
                    } else null
                    
                    val deliveryStatus = when (statusType) {
                        "Sending" -> EmergencyDeliveryStatus.Sending
                        "DeliveredInternet" -> EmergencyDeliveryStatus.DeliveredInternet(timestamp)
                        "DeliveredSMS" -> EmergencyDeliveryStatus.DeliveredSMS(timestamp)
                        "FailedRetrying" -> EmergencyDeliveryStatus.FailedRetrying(0, 3)
                        "PermanentlyFailed" -> EmergencyDeliveryStatus.PermanentlyFailed
                        else -> EmergencyDeliveryStatus.Sending
                    }
                    
                    SafeReturnState.Emergency(
                        reason = reason,
                        timestamp = timestamp,
                        deliveryStatus = deliveryStatus,
                        lastLocation = location
                    )
                }
                "arrived" -> SafeReturnState.Arrived("Restored", 0, 0.0)
                "failure" -> SafeReturnState.Failure("Restored failure", true, lastUpdate)
                else -> null
            }
        } catch (e: Exception) {
            clearSnapshot()
            null
        }
    }
    
    suspend fun clearSnapshot() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    suspend fun getLastUpdateTime(): Long {
        return dataStore.data.first()[LAST_UPDATE_KEY] ?: 0L
    }
}
