package com.example.regresoacasa.core.emergency

import android.content.Context
import android.telephony.SmsManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.regresoacasa.core.EmergencyDeliveryStatus
import com.example.regresoacasa.domain.model.UbicacionUsuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmergencyRetryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val smsManager = context.getSystemService(SmsManager::class.java)
    
    override suspend fun doWork(): Result {
        val emergencyId = inputData.getString("emergencyId") ?: return Result.failure()
        val reason = inputData.getString("reason") ?: "Unknown"
        val contacts = inputData.getStringArray("contacts")?.toList() ?: emptyList()
        val lat = inputData.getDouble("lat", 0.0)
        val lng = inputData.getDouble("lng", 0.0)
        val batteryLevel = inputData.getInt("batteryLevel", 100)
        val attempt = inputData.getInt("attempt", 0)
        
        val location = if (lat != 0.0 || lng != 0.0) {
            UbicacionUsuario(lat, lng)
        } else null
        
        val message = formatMessage(reason, location, batteryLevel)
        
        // Try backend first
        val backendSuccess = trySendBackend(emergencyId, message, location)
        
        if (backendSuccess) {
            persistEmergency(emergencyId, reason, contacts, location, "backend")
            return Result.success()
        }
        
        // Fallback to SMS
        val smsSuccess = trySendSMS(contacts, message)
        
        if (smsSuccess) {
            persistEmergency(emergencyId, reason, contacts, location, "sms")
            return Result.success()
        }
        
        // Retry if not at max attempts
        if (attempt < 3) {
            return Result.retry()
        }
        
        // Mark as permanently failed
        persistEmergency(emergencyId, reason, contacts, location, "failed")
        return Result.failure()
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
                .url("https://your-backend.com/api/emergency")
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
        try {
            val db = (applicationContext as com.example.regresoacasa.RegresoACasaApp).database
            val entity = com.example.regresoacasa.data.local.entity.EmergencyAlertEntity(
                id = emergencyId,
                reason = reason,
                contacts = contacts.joinToString(","),
                latitude = location?.latitud ?: 0.0,
                longitude = location?.longitud ?: 0.0,
                timestamp = System.currentTimeMillis(),
                deliveryMethod = method,
                status = if (method == "failed") "failed" else "delivered"
            )
            kotlinx.coroutines.GlobalScope.launch {
                db.emergencyAlertDao().insert(entity)
            }
        } catch (e: Exception) {
            // Log but don't fail
        }
    }
}
