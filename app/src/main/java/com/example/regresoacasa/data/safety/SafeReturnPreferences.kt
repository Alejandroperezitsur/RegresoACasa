package com.example.regresoacasa.data.safety

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension para obtener el DataStore
val Context.safeReturnDataStore: DataStore<Preferences> by preferencesDataStore(name = "safe_return_preferences")

/**
 * Preferencias de DataStore para el modo Regreso Seguro
 * Persiste la sesión activa para recuperación tras reinicio de app
 */
class SafeReturnPreferences(private val context: Context) {
    
    companion object {
        val IS_ACTIVE = booleanPreferencesKey("safe_return_active")
        val CONTACT_PHONE = stringPreferencesKey("contact_phone")
        val START_TIME = longPreferencesKey("start_time")
        val ETA_MINUTES = intPreferencesKey("eta_minutes")
        val DESTINATION_LAT = stringPreferencesKey("dest_lat")
        val DESTINATION_LNG = stringPreferencesKey("dest_lng")
        val DESTINATION_ADDRESS = stringPreferencesKey("dest_address")
        val SESSION_ID = stringPreferencesKey("session_id")
    }
    
    /**
     * Guarda una sesión activa de Regreso Seguro
     */
    suspend fun saveSession(session: SafeReturnSession) {
        context.safeReturnDataStore.edit { prefs ->
            prefs[IS_ACTIVE] = session.isActive
            prefs[CONTACT_PHONE] = session.contactPhone
            prefs[START_TIME] = session.startTime
            prefs[ETA_MINUTES] = session.etaMinutes
            prefs[DESTINATION_LAT] = session.destinationLat.toString()
            prefs[DESTINATION_LNG] = session.destinationLng.toString()
            prefs[DESTINATION_ADDRESS] = session.destinationAddress
            prefs[SESSION_ID] = session.sessionId
        }
    }
    
    /**
     * Obtiene la sesión guardada
     */
    val sessionFlow: Flow<SafeReturnSession?> = context.safeReturnDataStore.data.map { prefs ->
        val isActive = prefs[IS_ACTIVE] ?: false
        
        if (!isActive) return@map null
        
        SafeReturnSession(
            isActive = isActive,
            contactPhone = prefs[CONTACT_PHONE] ?: "",
            startTime = prefs[START_TIME] ?: 0L,
            etaMinutes = prefs[ETA_MINUTES] ?: 0,
            destinationLat = prefs[DESTINATION_LAT]?.toDoubleOrNull() ?: 0.0,
            destinationLng = prefs[DESTINATION_LNG]?.toDoubleOrNull() ?: 0.0,
            destinationAddress = prefs[DESTINATION_ADDRESS] ?: "",
            sessionId = prefs[SESSION_ID] ?: ""
        )
    }
    
    /**
     * Limpia la sesión (cuando se completa o cancela)
     */
    suspend fun clearSession() {
        context.safeReturnDataStore.edit { prefs ->
            prefs[IS_ACTIVE] = false
            prefs.remove(CONTACT_PHONE)
            prefs.remove(START_TIME)
            prefs.remove(ETA_MINUTES)
            prefs.remove(DESTINATION_LAT)
            prefs.remove(DESTINATION_LNG)
            prefs.remove(DESTINATION_ADDRESS)
            prefs.remove(SESSION_ID)
        }
    }
    
    /**
     * Verifica si hay una sesión activa válida (< 2 horas)
     */
    suspend fun hasValidSession(): Boolean {
        val session = sessionFlow.firstOrNull()
        return session?.isValid() ?: false
    }
}

/**
 * Datos de una sesión de Regreso Seguro
 */
data class SafeReturnSession(
    val isActive: Boolean,
    val contactPhone: String,
    val startTime: Long,
    val etaMinutes: Int,
    val destinationLat: Double,
    val destinationLng: Double,
    val destinationAddress: String,
    val sessionId: String
) {
    /**
     * Verifica si la sesión sigue siendo válida (< 2 horas desde inicio)
     */
    fun isValid(): Boolean {
        if (!isActive) return false
        val twoHoursInMillis = 2 * 60 * 60 * 1000
        return (System.currentTimeMillis() - startTime) < twoHoursInMillis
    }
    
    /**
     * Tiempo restante estimado en minutos
     */
    fun getElapsedMinutes(): Int {
        return ((System.currentTimeMillis() - startTime) / 1000 / 60).toInt()
    }
}
