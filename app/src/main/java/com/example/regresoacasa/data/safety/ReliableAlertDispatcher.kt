package com.example.regresoacasa.data.safety

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.example.regresoacasa.data.local.AlertDeliveryDao
import com.example.regresoacasa.data.local.entity.AlertDeliveryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class ReliableAlertDispatcher(
    private val context: Context,
    private val alertDeliveryDao: AlertDeliveryDao,
    private val scope: CoroutineScope
) {
    companion object {
        private const val ACTION_SMS_SENT = "com.example.regresoacasa.SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.example.regresoacasa.SMS_DELIVERED"
        private const val EXTRA_ALERT_ID = "alert_id"
        private const val EXTRA_CONTACT_PHONE = "contact_phone"
        private const val MAX_RETRIES = 3
        private const val DELIVERY_TIMEOUT_MS = 30000L
        private const val RETRY_DELAY_MS = 5000L
    }

    private val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
    private val pendingAlerts = mutableMapOf<String, AlertDeliveryEntity>()
    private var deliveryCheckJob: Job? = null
    private var isReceiverRegistered = false

    private val sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val alertId = intent?.getStringExtra(EXTRA_ALERT_ID) ?: return
            val contactPhone = intent.getStringExtra(EXTRA_CONTACT_PHONE) ?: return
            val resultCode = resultCode

            scope.launch(Dispatchers.IO) {
                handleSendResult(alertId, contactPhone, resultCode)
            }
        }
    }

    private val deliveredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val alertId = intent?.getStringExtra(EXTRA_ALERT_ID) ?: return
            val contactPhone = intent.getStringExtra(EXTRA_CONTACT_PHONE) ?: return
            val resultCode = resultCode

            scope.launch(Dispatchers.IO) {
                handleDeliveryResult(alertId, contactPhone, resultCode)
            }
        }
    }

    fun registerReceivers() {
        if (isReceiverRegistered) return

        val sentFilter = IntentFilter(ACTION_SMS_SENT)
        context.registerReceiver(sentReceiver, sentFilter)

        val deliveredFilter = IntentFilter(ACTION_SMS_DELIVERED)
        context.registerReceiver(deliveredReceiver, deliveredFilter)

        isReceiverRegistered = true
        startDeliveryCheckLoop()
        Log.d("ReliableAlertDispatcher", "Broadcast receivers registered")
    }

    fun unregisterReceivers() {
        if (!isReceiverRegistered) return

        try {
            context.unregisterReceiver(sentReceiver)
            context.unregisterReceiver(deliveredReceiver)
        } catch (e: Exception) {
            Log.e("ReliableAlertDispatcher", "Error unregistering receivers", e)
        }

        isReceiverRegistered = false
        deliveryCheckJob?.cancel()
        Log.d("ReliableAlertDispatcher", "Broadcast receivers unregistered")
    }

    suspend fun sendAlert(
        contactPhone: String,
        message: String,
        locationLat: Double? = null,
        locationLng: Double? = null,
        batteryLevel: Int? = null,
        tripId: String? = null
    ): AlertResult {
        val alertId = UUID.randomUUID().toString()
        
        val alertEntity = AlertDeliveryEntity(
            alertId = alertId,
            contactPhone = contactPhone,
            message = message,
            sendStatus = AlertDeliveryEntity.SendStatus.PENDING,
            deliveryStatus = AlertDeliveryEntity.DeliveryStatus.UNKNOWN,
            retryCount = 0,
            sentTimestamp = System.currentTimeMillis(),
            deliveredTimestamp = null,
            locationLat = locationLat,
            locationLng = locationLng,
            batteryLevel = batteryLevel,
            tripId = tripId
        )

        alertDeliveryDao.insertAlert(alertEntity)
        pendingAlerts[alertId] = alertEntity

        return sendSmsWithRetry(alertEntity)
    }

    private suspend fun sendSmsWithRetry(alert: AlertDeliveryEntity): AlertResult {
        var currentAlert = alert
        var lastError: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                sendSingleSms(currentAlert)
                return AlertResult.Success(currentAlert.alertId)
            } catch (e: Exception) {
                lastError = e
                Log.w("ReliableAlertDispatcher", "SMS send attempt ${attempt + 1} failed for ${currentAlert.contactPhone}", e)
                
                currentAlert = currentAlert.copy(
                    retryCount = attempt + 1,
                    sendStatus = AlertDeliveryEntity.SendStatus.PENDING
                )
                alertDeliveryDao.updateAlert(currentAlert)
                pendingAlerts[currentAlert.alertId] = currentAlert

                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        currentAlert = currentAlert.copy(
            sendStatus = AlertDeliveryEntity.SendStatus.FAILED
        )
        alertDeliveryDao.updateAlert(currentAlert)
        pendingAlerts.remove(currentAlert.alertId)

        Log.e("ReliableAlertDispatcher", "SMS failed completely for ${currentAlert.contactPhone} after $MAX_RETRIES retries")
        return AlertResult.Failed(currentAlert.alertId, lastError)
    }

    private fun sendSingleSms(alert: AlertDeliveryEntity) {
        val sentIntent = Intent(ACTION_SMS_SENT).apply {
            putExtra(EXTRA_ALERT_ID, alert.alertId)
            putExtra(EXTRA_CONTACT_PHONE, alert.contactPhone)
        }

        val deliveredIntent = Intent(ACTION_SMS_DELIVERED).apply {
            putExtra(EXTRA_ALERT_ID, alert.alertId)
            putExtra(EXTRA_CONTACT_PHONE, alert.contactPhone)
        }

        val sentPendingIntent = PendingIntent.getBroadcast(
            context,
            alert.alertId.hashCode(),
            sentIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val deliveredPendingIntent = PendingIntent.getBroadcast(
            context,
            alert.alertId.hashCode() + 1,
            deliveredIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        try {
            smsManager.sendTextMessage(
                alert.contactPhone,
                null,
                alert.message,
                sentPendingIntent,
                deliveredPendingIntent
            )
            Log.d("ReliableAlertDispatcher", "SMS sent to ${alert.contactPhone}, waiting for confirmation")
        } catch (e: Exception) {
            sentPendingIntent.cancel()
            deliveredPendingIntent.cancel()
            throw e
        }
    }

    private suspend fun handleSendResult(alertId: String, contactPhone: String, resultCode: Int) {
        val alert = pendingAlerts[alertId] ?: alertDeliveryDao.getAlertById(alertId) ?: return

        val updatedAlert = when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                Log.d("ReliableAlertDispatcher", "SMS sent successfully to $contactPhone")
                alert.copy(sendStatus = AlertDeliveryEntity.SendStatus.SENT)
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE,
            SmsManager.RESULT_ERROR_NO_SERVICE,
            SmsManager.RESULT_ERROR_NULL_PDU,
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Log.e("ReliableAlertDispatcher", "SMS send failed to $contactPhone, code: $resultCode")
                alert.copy(sendStatus = AlertDeliveryEntity.SendStatus.FAILED)
            }
            else -> {
                Log.w("ReliableAlertDispatcher", "Unknown SMS send result to $contactPhone, code: $resultCode")
                alert.copy(sendStatus = AlertDeliveryEntity.SendStatus.FAILED)
            }
        }

        alertDeliveryDao.updateAlert(updatedAlert)
        pendingAlerts[alertId] = updatedAlert

        if (updatedAlert.sendStatus == AlertDeliveryEntity.SendStatus.FAILED) {
            pendingAlerts.remove(alertId)
        }
    }

    private suspend fun handleDeliveryResult(alertId: String, contactPhone: String, resultCode: Int) {
        val alert = pendingAlerts[alertId] ?: alertDeliveryDao.getAlertById(alertId) ?: return

        val updatedAlert = when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                Log.d("ReliableAlertDispatcher", "SMS delivered successfully to $contactPhone")
                alert.copy(
                    deliveryStatus = AlertDeliveryEntity.DeliveryStatus.DELIVERED,
                    deliveredTimestamp = System.currentTimeMillis()
                )
            }
            else -> {
                Log.e("ReliableAlertDispatcher", "SMS delivery failed to $contactPhone, code: $resultCode")
                alert.copy(deliveryStatus = AlertDeliveryEntity.DeliveryStatus.FAILED)
            }
        }

        alertDeliveryDao.updateAlert(updatedAlert)
        pendingAlerts.remove(alertId)
    }

    private fun startDeliveryCheckLoop() {
        deliveryCheckJob?.cancel()
        deliveryCheckJob = scope.launch(Dispatchers.IO) {
            while (isReceiverRegistered) {
                delay(10000L)
                checkUncertainDeliveries()
            }
        }
    }

    private suspend fun checkUncertainDeliveries() {
        val threshold = System.currentTimeMillis() - DELIVERY_TIMEOUT_MS
        val uncertainAlerts = alertDeliveryDao.getUncertainDeliveries(threshold)

        uncertainAlerts.forEach { alert ->
            Log.w("ReliableAlertDispatcher", "Marking alert ${alert.alertId} as UNCERTAIN - delivery not confirmed in 30s")
            val updatedAlert = alert.copy(
                deliveryStatus = AlertDeliveryEntity.DeliveryStatus.UNCERTAIN
            )
            alertDeliveryDao.updateAlert(updatedAlert)
            pendingAlerts.remove(alert.alertId)
        }
    }

    suspend fun retryPendingAlerts() {
        val pendingAlerts = alertDeliveryDao.getPendingAlerts()
        Log.d("ReliableAlertDispatcher", "Retrying ${pendingAlerts.size} pending alerts")

        pendingAlerts.forEach { alert ->
            val result = sendSmsWithRetry(alert)
            Log.d("ReliableAlertDispatcher", "Retry result for ${alert.contactPhone}: $result")
        }
    }

    sealed class AlertResult {
        data class Success(val alertId: String) : AlertResult()
        data class Failed(val alertId: String, val error: Exception?) : AlertResult()
    }
}
