package com.example.regresoacasa.core.safety.alert

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * V3 FASE 2 — ALERTA CON CONFIRMACIÓN REAL
 * 
 * Sistema de tracking de entrega de SMS con:
 * - Sent callback (enviado)
 * - Delivery callback (entregado)
 * - Estado persistente
 * - Timeout para delivery
 * - Retry automático
 */
class DeliveryTracker(
    private val context: Context,
    private val scope: CoroutineScope,
    private val persistence: AlertPersistence
) {
    
    private val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
    
    private val _deliveryState = MutableStateFlow<Map<String, DeliveryStatus>>(emptyMap())
    val deliveryState: StateFlow<Map<String, DeliveryStatus>> = _deliveryState.asStateFlow()
    
    private var timeoutCheckJob: Job? = null
    private var isReceiverRegistered = false
    
    // Callbacks
    var onDeliveryConfirmed: ((String) -> Unit)? = null
    var onDeliveryFailed: ((String, String) -> Unit)? = null
    
    /**
     * Estado de entrega de una alerta
     */
    sealed class DeliveryStatus {
        object Pending : DeliveryStatus()
        data class Sent(val timestamp: Long) : DeliveryStatus()
        data class Delivered(val timestamp: Long) : DeliveryStatus()
        data class Failed(val reason: String, val timestamp: Long) : DeliveryStatus()
        data class Uncertain(val reason: String, val timestamp: Long) : DeliveryStatus()
    }
    
    /**
     * Resultado completo de una alerta
     */
    data class AlertResult(
        val alertId: String,
        val sent: Boolean,
        val delivered: Boolean,
        val sentTimestamp: Long?,
        val deliveredTimestamp: Long?,
        val failureReason: String?
    )
    
    // Receivers para SMS
    private val sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val alertId = intent?.getStringExtra(EXTRA_ALERT_ID) ?: return
            val resultCode = resultCode
            
            scope.launch(Dispatchers.IO) {
                handleSentResult(alertId, resultCode)
            }
        }
    }
    
    private val deliveredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val alertId = intent?.getStringExtra(EXTRA_ALERT_ID) ?: return
            val resultCode = resultCode
            
            scope.launch(Dispatchers.IO) {
                handleDeliveryResult(alertId, resultCode)
            }
        }
    }
    
    /**
     * Envía SMS con tracking de entrega
     */
    fun sendSMSWithTracking(
        alertId: String,
        phoneNumber: String,
        message: String
    ): AlertResult {
        // Crear intents para callbacks
        val sentIntent = Intent(ACTION_SMS_SENT).apply {
            putExtra(EXTRA_ALERT_ID, alertId)
            setPackage(context.packageName)
        }
        
        val deliveredIntent = Intent(ACTION_SMS_DELIVERED).apply {
            putExtra(EXTRA_ALERT_ID, alertId)
            setPackage(context.packageName)
        }
        
        val sentPendingIntent = PendingIntent.getBroadcast(
            context,
            alertId.hashCode(),
            sentIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        val deliveredPendingIntent = PendingIntent.getBroadcast(
            context,
            alertId.hashCode() + 1,
            deliveredIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        try {
            smsManager.sendTextMessage(
                phoneNumber,
                null,
                message,
                sentPendingIntent,
                deliveredPendingIntent
            )
            
            // Marcar como pending
            updateDeliveryStatus(alertId, DeliveryStatus.Pending)
            
            Log.d("DeliveryTracker", "SMS sent with tracking: $alertId")
            
            return AlertResult(
                alertId = alertId,
                sent = true,
                delivered = false,
                sentTimestamp = System.currentTimeMillis(),
                deliveredTimestamp = null,
                failureReason = null
            )
        } catch (e: Exception) {
            sentPendingIntent.cancel()
            deliveredPendingIntent.cancel()
            
            val failureReason = e.message ?: "Unknown error"
            updateDeliveryStatus(alertId, DeliveryStatus.Failed(failureReason, System.currentTimeMillis()))
            
            Log.e("DeliveryTracker", "SMS send failed: $alertId", e)
            
            return AlertResult(
                alertId = alertId,
                sent = false,
                delivered = false,
                sentTimestamp = null,
                deliveredTimestamp = null,
                failureReason = failureReason
            )
        }
    }
    
    /**
     * Maneja resultado de envío (sent callback)
     */
    private suspend fun handleSentResult(alertId: String, resultCode: Int) {
        val status = when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                Log.d("DeliveryTracker", "SMS sent successfully: $alertId")
                DeliveryStatus.Sent(System.currentTimeMillis())
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                Log.e("DeliveryTracker", "SMS generic failure: $alertId")
                DeliveryStatus.Failed("Generic failure", System.currentTimeMillis())
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                Log.e("DeliveryTracker", "SMS no service: $alertId")
                DeliveryStatus.Failed("No service", System.currentTimeMillis())
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                Log.e("DeliveryTracker", "SMS null PDU: $alertId")
                DeliveryStatus.Failed("Null PDU", System.currentTimeMillis())
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Log.e("DeliveryTracker", "SMS radio off: $alertId")
                DeliveryStatus.Failed("Radio off", System.currentTimeMillis())
            }
            else -> {
                Log.w("DeliveryTracker", "SMS unknown result: $alertId, code: $resultCode")
                DeliveryStatus.Failed("Unknown error", System.currentTimeMillis())
            }
        }
        
        updateDeliveryStatus(alertId, status)
        
        // Actualizar persistencia
        val pending = persistence.getPendingAlert(alertId)
        if (pending != null) {
            val updated = when (status) {
                is DeliveryStatus.Sent -> {
                    pending.copy(status = AlertStatus.SENT)
                }
                is DeliveryStatus.Failed -> {
                    pending.copy(status = AlertStatus.FAILED)
                }
                else -> pending
            }
            persistence.updatePendingAlert(updated)
        }
    }
    
    /**
     * Maneja resultado de entrega (delivery callback)
     */
    private suspend fun handleDeliveryResult(alertId: String, resultCode: Int) {
        val status = when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                Log.d("DeliveryTracker", "SMS delivered successfully: $alertId")
                DeliveryStatus.Delivered(System.currentTimeMillis())
            }
            else -> {
                Log.e("DeliveryTracker", "SMS delivery failed: $alertId, code: $resultCode")
                DeliveryStatus.Failed("Delivery failed", System.currentTimeMillis())
            }
        }
        
        updateDeliveryStatus(alertId, status)
        
        // Actualizar persistencia
        val pending = persistence.getPendingAlert(alertId)
        if (pending != null) {
            val updated = when (status) {
                is DeliveryStatus.Delivered -> {
                    onDeliveryConfirmed?.invoke(alertId)
                    pending.copy(
                        status = AlertStatus.DELIVERED,
                        deliveredTimestamp = System.currentTimeMillis()
                    )
                }
                is DeliveryStatus.Failed -> {
                    onDeliveryFailed?.invoke(alertId, "Delivery failed")
                    pending.copy(status = AlertStatus.FAILED)
                }
                else -> pending
            }
            persistence.updatePendingAlert(updated)
            
            // Si fue entregado, eliminar de pendientes
            if (status is DeliveryStatus.Delivered) {
                persistence.deletePendingAlert(alertId)
            }
        }
    }
    
    /**
     * Actualiza el estado de entrega
     */
    private fun updateDeliveryStatus(alertId: String, status: DeliveryStatus) {
        val current = _deliveryState.value.toMutableMap()
        current[alertId] = status
        _deliveryState.value = current
    }
    
    /**
     * Obtiene el resultado de una alerta
     */
    fun getAlertResult(alertId: String): AlertResult {
        val status = _deliveryState.value[alertId]
        
        return when (status) {
            is DeliveryStatus.Sent -> AlertResult(
                alertId = alertId,
                sent = true,
                delivered = false,
                sentTimestamp = status.timestamp,
                deliveredTimestamp = null,
                failureReason = null
            )
            is DeliveryStatus.Delivered -> AlertResult(
                alertId = alertId,
                sent = true,
                delivered = true,
                sentTimestamp = null, // No tenemos timestamp de sent
                deliveredTimestamp = status.timestamp,
                failureReason = null
            )
            is DeliveryStatus.Failed -> AlertResult(
                alertId = alertId,
                sent = false,
                delivered = false,
                sentTimestamp = null,
                deliveredTimestamp = null,
                failureReason = status.reason
            )
            is DeliveryStatus.Uncertain -> AlertResult(
                alertId = alertId,
                sent = true,
                delivered = false,
                sentTimestamp = null,
                deliveredTimestamp = null,
                failureReason = status.reason
            )
            DeliveryStatus.Pending -> AlertResult(
                alertId = alertId,
                sent = false,
                delivered = false,
                sentTimestamp = null,
                deliveredTimestamp = null,
                failureReason = "Pending"
            )
            null -> AlertResult(
                alertId = alertId,
                sent = false,
                delivered = false,
                sentTimestamp = null,
                deliveredTimestamp = null,
                failureReason = "Not found"
            )
        }
    }
    
    /**
     * Registra receivers
     */
    fun registerReceivers() {
        if (isReceiverRegistered) return
        
        context.registerReceiver(sentReceiver, android.content.IntentFilter(ACTION_SMS_SENT))
        context.registerReceiver(deliveredReceiver, android.content.IntentFilter(ACTION_SMS_DELIVERED))
        
        isReceiverRegistered = true
        
        // Iniciar verificación de timeouts
        startTimeoutCheck()
        
        Log.d("DeliveryTracker", "Receivers registered")
    }
    
    /**
     * Desregistra receivers
     */
    fun unregisterReceivers() {
        if (!isReceiverRegistered) return
        
        try {
            context.unregisterReceiver(sentReceiver)
            context.unregisterReceiver(deliveredReceiver)
        } catch (e: Exception) {
            Log.e("DeliveryTracker", "Error unregistering receivers", e)
        }
        
        isReceiverRegistered = false
        timeoutCheckJob?.cancel()
        
        Log.d("DeliveryTracker", "Receivers unregistered")
    }
    
    /**
     * Inicia verificación de timeouts
     */
    private fun startTimeoutCheck() {
        timeoutCheckJob?.cancel()
        timeoutCheckJob = scope.launch(Dispatchers.IO) {
            while (isReceiverRegistered) {
                delay(15000) // Verificar cada 15 segundos
                checkDeliveryTimeouts()
            }
        }
    }
    
    /**
     * Verifica timeouts de delivery
     */
    private suspend fun checkDeliveryTimeouts() {
        val now = System.currentTimeMillis()
        val timeout = 15000L // 15 segundos timeout
        
        _deliveryState.value.forEach { (alertId, status) ->
            if (status is DeliveryStatus.Sent) {
                if (now - status.timestamp > timeout) {
                    // Timeout - marcar como uncertain
                    Log.w("DeliveryTracker", "Delivery timeout: $alertId")
                    updateDeliveryStatus(
                        alertId,
                        DeliveryStatus.Uncertain("Delivery timeout", now)
                    )
                    
                    // Actualizar persistencia
                    val pending = persistence.getPendingAlert(alertId)
                    if (pending != null) {
                        persistence.updatePendingAlert(
                            pending.copy(status = AlertStatus.UNCERTAIN)
                        )
                    }
                }
            }
        }
    }
    
    companion object {
        private const val ACTION_SMS_SENT = "com.example.regresoacasa.SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.example.regresoacasa.SMS_DELIVERED"
        private const val EXTRA_ALERT_ID = "alert_id"
    }
}
