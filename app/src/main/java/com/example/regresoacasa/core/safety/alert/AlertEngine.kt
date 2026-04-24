package com.example.regresoacasa.core.safety.alert

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.example.regresoacasa.core.safety.SafetyConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Motor de Alertas Multicanal
 * 
 * Sistema de envío de alertas fault-tolerant:
 * - Intenta backend primero
 * - Si falla → SMS
 * - Si falla → retry persistente
 * - Funciona sin internet
 * - Funciona sin UI
 */
class AlertEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val persistence: AlertPersistence
) {
    
    private val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
    
    // V3: DeliveryTracker para tracking de entrega real
    private val deliveryTracker = DeliveryTracker(context, scope, persistence)
    
    private val _alertState = MutableStateFlow<AlertState>(AlertState.Idle)
    val alertState: StateFlow<AlertState> = _alertState.asStateFlow()
    
    private var deliveryCheckJob: Job? = null
    private var isReceiverRegistered = false
    
    // Callbacks para notificaciones
    var onAlertSent: ((String) -> Unit)? = null
    var onAlertFailed: ((String, Exception) -> Unit)? = null
    
    // V3: Callbacks de delivery
    var onDeliveryConfirmed: ((String) -> Unit)? = null
    var onDeliveryFailed: ((String, String) -> Unit)? = null
    
    init {
        // V3: Configurar callbacks de DeliveryTracker
        deliveryTracker.onDeliveryConfirmed = { alertId ->
            onDeliveryConfirmed?.invoke(alertId)
            Log.d("AlertEngine", "Delivery confirmed: $alertId")
        }
        
        deliveryTracker.onDeliveryFailed = { alertId, reason ->
            onDeliveryFailed?.invoke(alertId, reason)
            Log.e("AlertEngine", "Delivery failed: $alertId, reason: $reason")
        }
    }
    
    /**
     * Estado del motor de alertas
     */
    sealed class AlertState {
        object Idle : AlertState()
        data class Sending(val alertId: String) : AlertState()
        data class Retrying(val alertId: String, val attempt: Int) : AlertState()
        data class Sent(val alertId: String) : AlertState()
        data class Failed(val alertId: String, val reason: String) : AlertState()
    }
    
    /**
     * Resultado de envío de alerta
     */
    sealed class AlertResult {
        data class Success(val alertId: String) : AlertResult()
        data class Failed(val alertId: String, val error: Exception) : AlertResult()
        data class Pending(val alertId: String) : AlertResult()
    }
    
    // Receivers para confirmación de SMS
    private val sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val alertId = intent?.getStringExtra(EXTRA_ALERT_ID) ?: return
            val resultCode = resultCode
            
            scope.launch(Dispatchers.IO) {
                handleSendResult(alertId, resultCode)
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
     * Dispara una alerta de emergencia
     * 
     * @param reason Razón de la alerta
     * @param contactPhone Número de teléfono (opcional, usa contactos guardados si es null)
     * @param location Ubicación (opcional)
     * @param batteryLevel Nivel de batería (opcional)
     */
    suspend fun triggerEmergency(
        reason: String,
        contactPhone: String? = null,
        location: LocationData? = null,
        batteryLevel: Int? = null
    ): AlertResult {
        val alertId = UUID.randomUUID().toString()
        
        _alertState.value = AlertState.Sending(alertId)
        
        // Crear mensaje
        val message = formatEmergencyMessage(reason, location, batteryLevel)
        
        // Intentar backend primero
        val backendResult = trySendBackend(alertId, message, location)
        
        if (backendResult is AlertResult.Success) {
            _alertState.value = AlertState.Sent(alertId)
            onAlertSent?.invoke(alertId)
            return backendResult
        }
        
        // Backend falló, intentar SMS
        val smsResult = if (contactPhone != null) {
            sendSMSWithRetry(alertId, contactPhone, message)
        } else {
            AlertResult.Failed(alertId, Exception("No contact phone provided"))
        }
        
        return when (smsResult) {
            is AlertResult.Success -> {
                _alertState.value = AlertState.Sent(alertId)
                onAlertSent?.invoke(alertId)
                smsResult
            }
            is AlertResult.Failed -> {
                _alertState.value = AlertState.Failed(alertId, smsResult.error.message ?: "Unknown error")
                onAlertFailed?.invoke(alertId, smsResult.error)
                
                // Guardar para retry
                persistence.savePendingAlert(
                    PendingAlert(
                        id = alertId,
                        message = message,
                        contactPhone = contactPhone,
                        timestamp = System.currentTimeMillis(),
                        retries = 0
                    )
                )
                
                AlertResult.Pending(alertId)
            }
            else -> smsResult
        }
    }
    
    /**
     * Intenta enviar alerta vía backend
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun trySendBackend(
        alertId: String,
        message: String,
        location: LocationData?
    ): AlertResult {
        // TODO: Implementar envío al backend proxy
        // Por ahora, simulamos fallo para forzar SMS
        Log.d("AlertEngine", "Backend send attempted for $alertId")
        return AlertResult.Failed(alertId, Exception("Backend not implemented"))
    }
    
    /**
     * Envía SMS con reintentos
     */
    private suspend fun sendSMSWithRetry(
        alertId: String,
        contactPhone: String,
        message: String
    ): AlertResult {
        var lastError: Exception? = null
        
        repeat(SafetyConstants.ALERT_RETRY_LIMIT) { attempt ->
            try {
                sendSingleSMS(alertId, contactPhone, message)
                return AlertResult.Success(alertId)
            } catch (e: Exception) {
                lastError = e
                Log.w("AlertEngine", "SMS attempt ${attempt + 1} failed", e)
                
                if (attempt < SafetyConstants.ALERT_RETRY_LIMIT - 1) {
                    delay(SafetyConstants.ALERT_RETRY_DELAY_BASE * (attempt + 1))
                }
            }
        }
        
        return AlertResult.Failed(alertId, lastError ?: Exception("Unknown error"))
    }
    
    /**
     * Envía un SMS individual usando DeliveryTracker V3
     */
    private fun sendSingleSMS(alertId: String, contactPhone: String, message: String) {
        // V3: Usar DeliveryTracker para tracking de entrega real
        val result = deliveryTracker.sendSMSWithTracking(alertId, contactPhone, message)
        
        if (!result.sent) {
            throw Exception(result.failureReason ?: "SMS send failed")
        }
        
        Log.d("AlertEngine", "SMS sent with tracking to $contactPhone")
    }
    
    /**
     * Maneja resultado de envío de SMS
     */
    private suspend fun handleSendResult(alertId: String, resultCode: Int) {
        val pending = persistence.getPendingAlert(alertId) ?: return
        
        val updated = when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                Log.d("AlertEngine", "SMS sent successfully")
                pending.copy(status = AlertStatus.SENT)
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE,
            SmsManager.RESULT_ERROR_NO_SERVICE,
            SmsManager.RESULT_ERROR_NULL_PDU,
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Log.e("AlertEngine", "SMS send failed, code: $resultCode")
                pending.copy(status = AlertStatus.FAILED)
            }
            else -> {
                Log.w("AlertEngine", "Unknown SMS result, code: $resultCode")
                pending.copy(status = AlertStatus.FAILED)
            }
        }
        
        persistence.updatePendingAlert(updated)
    }
    
    /**
     * Maneja resultado de delivery de SMS
     */
    private suspend fun handleDeliveryResult(alertId: String, resultCode: Int) {
        val pending = persistence.getPendingAlert(alertId) ?: return
        
        val updated = when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                Log.d("AlertEngine", "SMS delivered successfully")
                pending.copy(
                    status = AlertStatus.DELIVERED,
                    deliveredTimestamp = System.currentTimeMillis()
                )
            }
            else -> {
                Log.e("AlertEngine", "SMS delivery failed, code: $resultCode")
                pending.copy(status = AlertStatus.FAILED)
            }
        }
        
        persistence.updatePendingAlert(updated)
        
        // Si fue entregado, eliminar de pendientes
        if (updated.status == AlertStatus.DELIVERED) {
            persistence.deletePendingAlert(alertId)
        }
    }
    
    /**
     * Reintenta alertas pendientes
     */
    suspend fun retryPendingAlerts() {
        val pendingAlerts = persistence.getPendingAlerts()
        
        pendingAlerts.forEach { alert ->
            if (alert.retries < SafetyConstants.ALERT_RETRY_LIMIT) {
                _alertState.value = AlertState.Retrying(alert.id, alert.retries + 1)
                
                val phone = alert.contactPhone ?: return@forEach
                val result = sendSMSWithRetry(alert.id, phone, alert.message)
                
                when (result) {
                    is AlertResult.Success -> {
                        persistence.deletePendingAlert(alert.id)
                    }
                    is AlertResult.Failed -> {
                        persistence.updatePendingAlert(
                            alert.copy(retries = alert.retries + 1)
                        )
                    }
                    else -> {}
                }
            }
        }
    }
    
    /**
     * Formatea mensaje de emergencia
     */
    private fun formatEmergencyMessage(
        reason: String,
        location: LocationData?,
        batteryLevel: Int?
    ): String {
        val sb = StringBuilder()
        sb.append("🚨 ALERTA DE EMERGENCIA - Regreso a Casa\n")
        sb.append("Razón: $reason\n")
        
        if (location != null) {
            sb.append("Ubicación: https://maps.google.com/?q=${location.latitude},${location.longitude}\n")
        }
        
        if (batteryLevel != null) {
            sb.append("Batería: $batteryLevel%\n")
        }
        
        sb.append("Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        
        return sb.toString()
    }
    
    /**
     * Registra receivers para confirmación de SMS
     */
    fun registerReceivers() {
        if (isReceiverRegistered) return
        
        context.registerReceiver(sentReceiver, android.content.IntentFilter(ACTION_SMS_SENT))
        context.registerReceiver(deliveredReceiver, android.content.IntentFilter(ACTION_SMS_DELIVERED))
        
        isReceiverRegistered = true
        
        // Iniciar loop de verificación de delivery
        startDeliveryCheckLoop()
        
        Log.d("AlertEngine", "SMS receivers registered")
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
            Log.e("AlertEngine", "Error unregistering receivers", e)
        }
        
        isReceiverRegistered = false
        deliveryCheckJob?.cancel()
        
        Log.d("AlertEngine", "SMS receivers unregistered")
    }
    
    /**
     * Inicia loop de verificación de delivery
     */
    private fun startDeliveryCheckLoop() {
        deliveryCheckJob?.cancel()
        deliveryCheckJob = scope.launch(Dispatchers.IO) {
            while (isReceiverRegistered) {
                delay(SafetyConstants.SMS_DELIVERY_TIMEOUT)
                checkUncertainDeliveries()
            }
        }
    }
    
    /**
     * Verifica deliveries inciertos
     */
    private suspend fun checkUncertainDeliveries() {
        val threshold = System.currentTimeMillis() - SafetyConstants.SMS_DELIVERY_TIMEOUT
        val uncertainAlerts = persistence.getUncertainDeliveries(threshold)
        
        uncertainAlerts.forEach { alert ->
            Log.w("AlertEngine", "Marking alert ${alert.id} as UNCERTAIN")
            persistence.updatePendingAlert(
                alert.copy(status = AlertStatus.UNCERTAIN)
            )
        }
    }
    
    companion object {
        private const val ACTION_SMS_SENT = "com.example.regresoacasa.SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.example.regresoacasa.SMS_DELIVERED"
        private const val EXTRA_ALERT_ID = "alert_id"
    }
}

/**
 * Datos de ubicación para alertas
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?
)

/**
 * Alerta pendiente para persistencia
 */
data class PendingAlert(
    val id: String,
    val message: String,
    val contactPhone: String?,
    val timestamp: Long,
    val retries: Int,
    val status: AlertStatus = AlertStatus.PENDING,
    val deliveredTimestamp: Long? = null
)

/**
 * Estado de alerta
 */
enum class AlertStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED,
    UNCERTAIN
}

/**
 * Interfaz de persistencia para alertas
 */
interface AlertPersistence {
    suspend fun savePendingAlert(alert: PendingAlert)
    suspend fun getPendingAlert(id: String): PendingAlert?
    suspend fun getPendingAlerts(): List<PendingAlert>
    suspend fun updatePendingAlert(alert: PendingAlert)
    suspend fun deletePendingAlert(id: String)
    suspend fun getUncertainDeliveries(threshold: Long): List<PendingAlert>
}
