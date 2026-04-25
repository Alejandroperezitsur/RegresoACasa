package com.example.regresoacasa.core.safety.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

/**
 * V3 FASE 13 — HARDENING FINAL (RATE LIMITING)
 * 
 * Sistema de rate limiting con persistencia en DataStore.
 * Limita la frecuencia de operaciones críticas y persiste en disco.
 */
class RateLimiter(private val context: Context) {
    
    private val requestHistory = ConcurrentHashMap<String, MutableList<Long>>()
    private val _isRateLimited = MutableStateFlow(false)
    val isRateLimited: StateFlow<Boolean> = _isRateLimited.asStateFlow()
    
    // Configuración de límites
    private val limits = mapOf(
        "alert" to RateLimit(maxRequests = 3, windowMs = 60000), // 3 alertas por minuto
        "location" to RateLimit(maxRequests = 10, windowMs = 1000), // 10 ubicaciones por segundo
        "network" to RateLimit(maxRequests = 30, windowMs = 60000), // 30 requests por minuto
        "sms" to RateLimit(maxRequests = 5, windowMs = 300000) // 5 SMS por 5 minutos
    )
    
    /**
     * Configuración de rate limit
     */
    data class RateLimit(
        val maxRequests: Int,
        val windowMs: Long
    )
    
    init {
        // Cargar historial persistido al inicio - no se puede llamar suspend en init
        // Se cargará bajo demanda en isAllowed
    }
    
    /**
     * Carga el historial persistido desde DataStore
     */
    private suspend fun loadPersistedHistoryIfNeeded() {
        try {
            val prefs = context.dataStore.data.first()
            limits.keys.forEach { operation ->
                val countKey = stringPreferencesKey("rate_count_$operation")
                val lastRequestKey = longPreferencesKey("rate_last_$operation")
                
                val count = prefs[countKey]?.toIntOrNull() ?: 0
                val lastRequest = prefs[lastRequestKey] ?: 0L
                
                if (count > 0 && lastRequest > 0) {
                    val now = System.currentTimeMillis()
                    val limit = limits[operation] ?: return@forEach
                    
                    // Si el último request está dentro de la ventana, restaurar
                    if (now - lastRequest < limit.windowMs) {
                        val history = requestHistory.getOrPut(operation) { mutableListOf() }
                        // Agregar timestamps simulados basados en el último request
                        for (i in 0 until count) {
                            history.add(lastRequest - (i * (limit.windowMs / count)))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Si falla la carga, continuar con historial vacío
        }
    }
    
    /**
     * Verifica si una operación está permitida
     */
    suspend fun isAllowed(operation: String): Boolean {
        // Cargar historial si es la primera vez
        if (!requestHistory.containsKey(operation)) {
            loadPersistedHistoryIfNeeded()
        }
        
        val limit = limits[operation] ?: return true
        
        val now = System.currentTimeMillis()
        val history = requestHistory.getOrPut(operation) { mutableListOf() }
        
        // Limpiar requests viejos fuera de la ventana
        history.removeIf { now - it > limit.windowMs }
        
        // Verificar si excede el límite
        if (history.size >= limit.maxRequests) {
            _isRateLimited.value = true
            return false
        }
        
        // Agregar request actual
        history.add(now)
        _isRateLimited.value = false
        
        // Persistir en DataStore
        persistHistory(operation, history.size, now)
        
        return true
    }
    
    /**
     * Persiste el historial en DataStore
     */
    private suspend fun persistHistory(operation: String, count: Int, lastRequest: Long) {
        try {
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("rate_count_$operation")] = count.toString()
                prefs[longPreferencesKey("rate_last_$operation")] = lastRequest
            }
        } catch (e: Exception) {
            // Si falla la persistencia, continuar sin ella
        }
    }
    
    /**
     * Obtiene el tiempo hasta que se permita la siguiente operación
     */
    fun getTimeUntilAllowed(operation: String): Long {
        val limit = limits[operation] ?: return 0L
        
        val history = requestHistory[operation] ?: return 0L
        if (history.isEmpty()) return 0L
        
        val now = System.currentTimeMillis()
        val oldestRequest = history.first()
        
        return (oldestRequest + limit.windowMs - now).coerceAtLeast(0L)
    }
    
    /**
     * Limpia el historial de una operación
     */
    suspend fun reset(operation: String) {
        requestHistory.remove(operation)
        try {
            context.dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("rate_count_$operation"))
                prefs.remove(longPreferencesKey("rate_last_$operation"))
            }
        } catch (e: Exception) {
            // Ignorar error al limpiar persistencia
        }
    }
    
    /**
     * Limpia todo el historial
     */
    suspend fun resetAll() {
        requestHistory.clear()
        try {
            context.dataStore.edit { prefs ->
                limits.keys.forEach { operation ->
                    prefs.remove(stringPreferencesKey("rate_count_$operation"))
                    prefs.remove(longPreferencesKey("rate_last_$operation"))
                }
            }
        } catch (e: Exception) {
            // Ignorar error al limpiar persistencia
        }
    }
}

// Extensión para DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rate_limiter")
