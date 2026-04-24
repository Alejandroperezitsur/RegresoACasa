package com.example.regresoacasa.core.emergency

import android.content.Context
import android.telephony.SmsManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.regresoacasa.core.EmergencyDeliveryStatus
import com.example.regresoacasa.core.SafeReturnState
import com.example.regresoacasa.domain.model.UbicacionUsuario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class EmergencyManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val backendUrl: String
) {
    
    private val _deliveryStatus = MutableStateFlow<EmergencyDeliveryStatus>(EmergencyDeliveryStatus.Sending)
    val deliveryStatus: StateFlow<EmergencyDeliveryStatus> = _deliveryStatus.asStateFlow()
    
    private val _lastEmergencyTime = MutableStateFlow<Long?>(null)
    val lastEmergencyTime: StateFlow<Long?> = _lastEmergencyTime.asStateFlow()
    
    private val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
    private var emergencyJob: Job? = null
    
    private val maxRetries = 3
    private val retryDelayBase = 5000L // 5 seconds
    
    fun triggerEmergency(
        reason: String,
        contacts: List<String>,
        location: UbicacionUsuario?,
        batteryLevel: Int
    ) {
        emergencyJob?.cancel()
        
        emergencyJob = scope.launch {
            _deliveryStatus.value = EmergencyDeliveryStatus.Sending
            _lastEmergencyTime.value = System.currentTimeMillis()
            
            val message = formatMessage(reason, location, batteryLevel)
            val emergencyId = UUID.randomUUID().toString()
            
            // Try backend first
            val backendSuccess = trySendBackend(emergencyId, message, location)
            
            if (backendSuccess) {
                _deliveryStatus.value = EmergencyDeliveryStatus.DeliveredInternet(System.currentTimeMillis())
                persistEmergency(emergencyId, reason, contacts, location, "backend")
            } else {
                // Fallback to SMS
                val smsSuccess = trySendSMS(contacts, message)
                
                if (smsSuccess) {
                    _deliveryStatus.value = EmergencyDeliveryStatus.DeliveredSMS(System.currentTimeMillis())
                    persistEmergency(emergencyId, reason, contacts, location, "sms")
                } else {
                    // Schedule retry with WorkManager
                    scheduleRetry(emergencyId, reason, contacts, location, batteryLevel, 0)
                    _deliveryStatus.value = EmergencyDeliveryStatus.FailedRetrying(0, maxRetries)
                }
            }
        }
    }
    
    private suspend fun trySendBackend(
        emergencyId: String,
        message: String,
        location: UbicacionUsuario?
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val json = okhttp3.MediaType.parse("application/json; charset=utf-8")
            val payload = mapOf(
                "emergencyId" to emergencyId,
                "message" to message,
                "location" to if (location != null) mapOf(
                    "lat" to location.latitud,
                    "lng" to location.longitud
                ) else null,
                "timestamp" to System.currentTimeMillis()
            )
            
            val requestBody = okhttp3.RequestBody.create(
                json,
                com.google.gson.Gson().toJson(payload)
            )
            
            val request = okhttp3.Request.Builder()
                .url("$backendUrl/api/emergency")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun trySendSMS(
        contacts: List<String>,
        message: String
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            var successCount = 0
            contacts.forEach { phone ->
                try {
                    smsManager.sendTextMessage(phone, null, message, null, null)
                    successCount++
                } catch (e: Exception) {
                    // Continue with other contacts
                }
            }
            successCount > 0
        } catch (e: Exception) {
            false
        }
    }
    
    private fun scheduleRetry(
        emergencyId: String,
        reason: String,
        contacts: List<String>,
        location: UbicacionUsuario?,
        batteryLevel: Int,
        attempt: Int
    ) {
        if (attempt >= maxRetries) {
            _deliveryStatus.value = EmergencyDeliveryStatus.PermanentlyFailed
            return
        }
        
        val workRequest = OneTimeWorkRequestBuilder<EmergencyRetryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                Data.Builder()
                    .putString("emergencyId", emergencyId)
                    .putString("reason", reason)
                    .putStringArray("contacts", contacts.toTypedArray())
                    .putDouble("lat", location?.latitud ?: 0.0)
                    .putDouble("lng", location?.longitud ?: 0.0)
                    .putInt("batteryLevel", batteryLevel)
                    .putInt("attempt", attempt)
                    .build()
            )
            .build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
    }
    
    private fun formatMessage(
        reason: String,
        location: UbicacionUsuario?,
        batteryLevel: Int
    ): String {
        val sb = StringBuilder()
        sb.append("🚨 EMERGENCIA - Regreso Seguro\n")
        sb.append("Razón: $reason\n")
        
        if (location != null) {
            sb.append("Ubicación: https://maps.google.com/?q=${location.latitud},${location.longitud}\n")
        }
        
        sb.append("Batería: $batteryLevel%\n")
        sb.append("Hora: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        
        return sb.toString()
    }
    
    private fun persistEmergency(
        emergencyId: String,
        reason: String,
        contacts: List<String>,
        location: UbicacionUsuario?,
        method: String
    ) {
        // Persist to Room DB for history
        scope.launch(Dispatchers.IO) {
            try {
                val db = (context.applicationContext as com.example.regresoacasa.RegresoACasaApp).database
                val entity = com.example.regresoacasa.data.local.entity.EmergencyAlertEntity(
                    id = emergencyId,
                    reason = reason,
                    contacts = contacts.joinToString(","),
                    latitude = location?.latitud ?: 0.0,
                    longitude = location?.longitud ?: 0.0,
                    timestamp = System.currentTimeMillis(),
                    deliveryMethod = method,
                    status = "delivered"
                )
                db.emergencyAlertDao().insert(entity)
            } catch (e: Exception) {
                // Log but don't fail
            }
        }
    }
    
    fun clear() {
        emergencyJob?.cancel()
        _deliveryStatus.value = EmergencyDeliveryStatus.Sending
    }
}
