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
 * SmsManagerWrapper - Único punto de envío de SMS con delivery confirmation
 * 
 * Consolidación de todos los envíos de SMS en la app con:
 * - PendingIntent para sent callback
 * - PendingIntent para delivery callback
 * - Tracking de estado
 * - Retry automático
 * - Callbacks para UI
 */
class SmsManagerWrapper(
    private val context: Context,
    private val scope: CoroutineScope,
    private val persistence: AlertPersistence
) {
    
    private val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
    
    private val _smsState = MutableStateFlow<Map<String, SmsStatus>>(emptyMap())
    val smsState: StateFlow<Map<String, SmsStatus>> = _smsState.asStateFlow()
    
    private var timeoutCheckJob: Job? = null
    private var isReceiverRegistered = false
    
    // Callbacks para UI
    var onSmsSent: ((String, String) -> Unit)? = null  // (smsId, phoneNumber)
    var onSmsDelivered: ((String, String) -> Unit)? = null  // (smsId, phoneNumber)
    var onSmsFailed: ((String, String, String) -> Unit)? = null  // (smsId, phoneNumber, reason)
    
    /**
     * Estado de un SMS
     */
    sealed class SmsStatus {
        object Pending : SmsStatus()
        data class Sent(val timestamp: Long) : SmsStatus()
        data class Delivered(val timestamp: Long) : SmsStatus()
        data class Failed(val reason: String, val timestamp: Long) : SmsStatus()
        data class Uncertain(val reason: String, val timestamp: Long) : SmsStatus()
    }
    
    /**
     * Resultado de envío de SMS
     */
    data class SmsResult(
        val smsId: String,
        val phoneNumber: String,
        val sent: Boolean,
        val delivered: Boolean,
        val sentTimestamp: Long?,
        val deliveredTimestamp: Long?,
        val failureReason: String?
    )
    
    // Receivers para SMS
    private val sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val smsId = intent?.getStringExtra(EXTRA_SMS_ID) ?: return
            val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: return
            val resultCode = resultCode
            
            scope.launch(Dispatchers.IO) {
                handleSentResult(smsId, phoneNumber, resultCode)
            }
        }
    }
    
    private val deliveredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val smsId = intent?.getStringExtra(EXTRA_SMS_ID) ?: return
            val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: return
            val resultCode = resultCode
            
            scope.launch(Dispatchers.IO) {
                handleDeliveryResult(smsId, phoneNumber, resultCode)
            }
        }
    }
    
    /**
     * Envía SMS con tracking completo
     */
    suspend fun sendWithTracking(
        phoneNumber: String,
        message: String,
        smsId: String? = null
    ): SmsResult {
        val actualSmsId = smsId ?: generateSmsId()
        
        // Crear intents para callbacks
        val sentIntent = Intent(ACTION_SMS_SENT).apply {
            putExtra(EXTRA_SMS_ID, actualSmsId)
            putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            setPackage(context.packageName)
        }
        
        val deliveredIntent = Intent(ACTION_SMS_DELIVERED).apply {
            putExtra(EXTRA_SMS_ID, actualSmsId)
            putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            setPackage(context.packageName)
        }
        
        val sentPendingIntent = PendingIntent.getBroadcast(
            context,
            actualSmsId.hashCode(),
            sentIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        val deliveredPendingIntent = PendingIntent.getBroadcast(
            context,
            actualSmsId.hashCode() + 1,
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
            updateSmsStatus(actualSmsId, SmsStatus.Pending)
            
            // Guardar en persistencia
            persistence.savePendingAlert(
                PendingAlert(
                    id = actualSmsId,
                    message = message,
                    contactPhone = phoneNumber,
                    timestamp = System.currentTimeMillis(),
                    retries = 0
                )
            )
            
            Log.d("SmsManagerWrapper", "SMS sent with tracking: $actualSmsId to $phoneNumber")
            
            return SmsResult(
                smsId = actualSmsId,
                phoneNumber = phoneNumber,
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
            updateSmsStatus(actualSmsId, SmsStatus.Failed(failureReason, System.currentTimeMillis()))
            
            Log.e("SmsManagerWrapper", "SMS send failed: $actualSmsId to $phoneNumber", e)
            
            onSmsFailed?.invoke(actualSmsId, phoneNumber, failureReason)
            
            return SmsResult(
                smsId = actualSmsId,
                phoneNumber = phoneNumber,
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
    private suspend fun handleSentResult(smsId: String, phoneNumber: String, resultCode: Int) {
        val status = when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                Log.d("SmsManagerWrapper", "SMS sent successfully: $smsId to $phoneNumber")
                onSmsSent?.invoke(smsId, phoneNumber)
                SmsStatus.Sent(System.currentTimeMillis())
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                Log.e("SmsManagerWrapper", "SMS generic failure: $smsId to $phoneNumber")
                onSmsFailed?.invoke(smsId, phoneNumber, "Generic failure")
                SmsStatus.Failed("Generic failure", System.currentTimeMillis())
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                Log.e("SmsManagerWrapper", "SMS no service: $smsId to $phoneNumber")
                onSmsFailed?.invoke(smsId, phoneNumber, "No service")
                SmsStatus.Failed("No service", System.currentTimeMillis())
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                Log.e("SmsManagerWrapper", "SMS null PDU: $smsId to $phoneNumber")
                onSmsFailed?.invoke(smsId, phoneNumber, "Null PDU")
                SmsStatus.Failed("Null PDU", System.currentTimeMillis())
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Log.e("SmsManagerWrapper", "SMS radio off: $smsId to $phoneNumber")
                onSmsFailed?.invoke(smsId, phoneNumber, "Radio off")
                SmsStatus.Failed("Radio off", System.currentTimeMillis())
            }
            else -> {
                Log.w("SmsManagerWrapper", "SMS unknown result: $smsId to $phoneNumber, code: $resultCode")
                onSmsFailed?.invoke(smsId, phoneNumber, "Unknown error")
                SmsStatus.Failed("Unknown error", System.currentTimeMillis())
            }
        }
        
        updateSmsStatus(smsId, status)
        
        // Actualizar persistencia
        val pending = persistence.getPendingAlert(smsId)
        if (pending != null) {
            val updated = when (status) {
                is SmsStatus.Sent -> {
                    pending.copy(status = AlertStatus.SENT)
                }
                is SmsStatus.Failed -> {
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
    private suspend fun handleDeliveryResult(smsId: String, phoneNumber: String, resultCode: Int) {
        val status = when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                Log.d("SmsManagerWrapper", "SMS delivered successfully: $smsId to $phoneNumber")
                onSmsDelivered?.invoke(smsId, phoneNumber)
                SmsStatus.Delivered(System.currentTimeMillis())
            }
            else -> {
                Log.e("SmsManagerWrapper", "SMS delivery failed: $smsId to $phoneNumber, code: $resultCode")
                onSmsFailed?.invoke(smsId, phoneNumber, "Delivery failed")
                SmsStatus.Failed("Delivery failed", System.currentTimeMillis())
            }
        }
        
        updateSmsStatus(smsId, status)
        
        // Actualizar persistencia
        val pending = persistence.getPendingAlert(smsId)
        if (pending != null) {
            val updated = when (status) {
                is SmsStatus.Delivered -> {
                    pending.copy(
                        status = AlertStatus.DELIVERED,
                        deliveredTimestamp = System.currentTimeMillis()
                    )
                }
                is SmsStatus.Failed -> {
                    pending.copy(status = AlertStatus.FAILED)
                }
                else -> pending
            }
            persistence.updatePendingAlert(updated)
            
            // Si fue entregado, eliminar de pendientes
            if (status is SmsStatus.Delivered) {
                persistence.deletePendingAlert(smsId)
            }
        }
    }
    
    /**
     * Actualiza el estado de SMS
     */
    private fun updateSmsStatus(smsId: String, status: SmsStatus) {
        val current = _smsState.value.toMutableMap()
        current[smsId] = status
        _smsState.value = current
    }
    
    /**
     * Obtiene el resultado de un SMS
     */
    fun getSmsResult(smsId: String): SmsResult {
        val status = _smsState.value[smsId]
        
        return when (status) {
            is SmsStatus.Sent -> SmsResult(
                smsId = smsId,
                phoneNumber = "", // No guardamos phone number en status
                sent = true,
                delivered = false,
                sentTimestamp = status.timestamp,
                deliveredTimestamp = null,
                failureReason = null
            )
            is SmsStatus.Delivered -> SmsResult(
                smsId = smsId,
                phoneNumber = "",
                sent = true,
                delivered = true,
                sentTimestamp = null,
                deliveredTimestamp = status.timestamp,
                failureReason = null
            )
            is SmsStatus.Failed -> SmsResult(
                smsId = smsId,
                phoneNumber = "",
                sent = false,
                delivered = false,
                sentTimestamp = null,
                deliveredTimestamp = null,
                failureReason = status.reason
            )
            is SmsStatus.Uncertain -> SmsResult(
                smsId = smsId,
                phoneNumber = "",
                sent = true,
                delivered = false,
                sentTimestamp = null,
                deliveredTimestamp = null,
                failureReason = status.reason
            )
            SmsStatus.Pending -> SmsResult(
                smsId = smsId,
                phoneNumber = "",
                sent = false,
                delivered = false,
                sentTimestamp = null,
                deliveredTimestamp = null,
                failureReason = "Pending"
            )
            null -> SmsResult(
                smsId = smsId,
                phoneNumber = "",
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
        
        Log.d("SmsManagerWrapper", "Receivers registered")
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
            Log.e("SmsManagerWrapper", "Error unregistering receivers", e)
        }
        
        isReceiverRegistered = false
        timeoutCheckJob?.cancel()
        
        Log.d("SmsManagerWrapper", "Receivers unregistered")
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
        val timeout = 30000L // 30 segundos timeout (según SafetyConstants)
        
        _smsState.value.forEach { (smsId, status) ->
            if (status is SmsStatus.Sent) {
                if (now - status.timestamp > timeout) {
                    // Timeout - marcar como uncertain
                    Log.w("SmsManagerWrapper", "Delivery timeout: $smsId")
                    updateSmsStatus(
                        smsId,
                        SmsStatus.Uncertain("Delivery timeout", now)
                    )
                    
                    // Actualizar persistencia
                    val pending = persistence.getPendingAlert(smsId)
                    if (pending != null) {
                        persistence.updatePendingAlert(
                            pending.copy(status = AlertStatus.UNCERTAIN)
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Genera ID único para SMS
     */
    private fun generateSmsId(): String {
        return "sms_${System.currentTimeMillis()}_${(0..9999).random()}"
    }
    
    companion object {
        private const val ACTION_SMS_SENT = "com.example.regresoacasa.SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.example.regresoacasa.SMS_DELIVERED"
        private const val EXTRA_SMS_ID = "sms_id"
        private const val EXTRA_PHONE_NUMBER = "phone_number"
    }
}
