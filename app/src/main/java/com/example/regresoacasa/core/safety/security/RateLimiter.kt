package com.example.regresoacasa.core.safety.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * V3 FASE 13 — HARDENING FINAL (RATE LIMITING)
 * 
 * Sistema de rate limiting para prevenir abuso y ataques.
 * Limita la frecuencia de operaciones críticas.
 */
class RateLimiter {
    
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
    
    /**
     * Verifica si una operación está permitida
     */
    fun isAllowed(operation: String): Boolean {
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
        return true
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
    fun reset(operation: String) {
        requestHistory.remove(operation)
    }
    
    /**
     * Limpia todo el historial
     */
    fun resetAll() {
        requestHistory.clear()
    }
}
