package com.example.regresoacasa.core.safety.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/**
 * V3 FASE 13 — HARDENING FINAL (BROADCAST PROTECTION)
 * 
 * Protege los broadcasts del sistema contra intercepción y spoofing.
 * Usa permisos personalizados y verifica el origen del broadcast.
 */
class BroadcastProtector(private val context: Context) {
    
    /**
     * Registra un receiver con protección
     */
    fun registerProtectedReceiver(
        receiver: BroadcastReceiver,
        action: String,
        permission: String? = null
    ) {
        val filter = IntentFilter(action)
        
        // En Android 8+, agregar permiso para proteger broadcasts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (permission != null) {
                context.registerReceiver(receiver, filter, permission, null, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter, null, null, Context.RECEIVER_NOT_EXPORTED)
            }
        } else {
            if (permission != null) {
                context.registerReceiver(receiver, filter, permission, null)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }
        
        Log.d("BroadcastProtector", "Registered protected receiver for: $action")
    }
    
    /**
     * Verifica si un intent es confiable
     */
    fun isIntentTrusted(intent: Intent): Boolean {
        // Verificar que el intent no sea null
        if (intent.action == null) {
            return false
        }
        
        // Verificar que el action sea conocido
        val knownActions = setOf(
            "com.example.regresoacasa.SMS_SENT",
            "com.example.regresoacasa.SMS_DELIVERED",
            "com.example.regresoacasa.CRITICAL_ALERT",
            "com.example.regresoacasa.RESTART_SERVICE"
        )
        
        if (!knownActions.contains(intent.action)) {
            Log.w("BroadcastProtector", "Unknown action: ${intent.action}")
            return false
        }
        
        // Verificar que el package sea el de la app
        if (intent.`package` != null && intent.`package` != context.packageName) {
            Log.w("BroadcastProtector", "Suspicious package: ${intent.`package`}")
            return false
        }
        
        return true
    }
    
    /**
     * Crea un intent protegido
     */
    fun createProtectedIntent(action: String): Intent {
        val intent = Intent(action)
        intent.`package` = context.packageName
        return intent
    }
}
