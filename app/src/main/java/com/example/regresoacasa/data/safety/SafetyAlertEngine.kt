package com.example.regresoacasa.data.safety

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.regresoacasa.MainActivity
import com.example.regresoacasa.core.safety.alert.SmsManagerWrapper
import com.example.regresoacasa.domain.model.UbicacionUsuario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SafetyAlertEngine - Sistema de alertas de 3 niveles
 * Nivel 1 (SOFT): Notificación al usuario "¿Todo bien?"
 * Nivel 2 (ACTIVE): Countdown 15s, si no responde → enviar a contactos
 * Nivel 3 (CRITICAL): SMS automático con confirmación de entrega
 */
class SafetyAlertEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val smsWrapper: SmsManagerWrapper
) {
    private val _alertState = MutableStateFlow(AlertState())
    val alertState = _alertState.asStateFlow()

    private var countdownJob: Job? = null
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object {
        private const val CHANNEL_ID = "safety_alerts"
        private const val NOTIFICATION_ID_SOFT = 1001
        private const val NOTIFICATION_ID_ACTIVE = 1002
        private const val NOTIFICATION_ID_CRITICAL = 1003
        private const val COUNTDOWN_SECONDS = 15
    }

    data class AlertState(
        val currentLevel: AlertLevel = AlertLevel.NONE,
        val countdownSeconds: Int = 0,
        val isCountingDown: Boolean = false,
        val lastAlertTime: Long = 0,
        val alertMessage: String = ""
    )

    init {
        createNotificationChannel()
    }

    /**
     * Procesa cambio de nivel de alerta desde TripMonitorEngine
     */
    fun processAlertLevelChange(
        newLevel: AlertLevel,
        location: UbicacionUsuario?,
        contacts: List<EmergencyContact>
    ) {
        when (newLevel) {
            AlertLevel.NONE -> clearAllAlerts()
            AlertLevel.SOFT -> triggerSoftAlert()
            AlertLevel.ACTIVE -> triggerActiveAlert(location, contacts)
            AlertLevel.CRITICAL -> triggerCriticalAlert(location, contacts)
        }
    }

    /**
     * NIVEL 1: Alerta suave - notificación al usuario
     */
    private fun triggerSoftAlert() {
        if (_alertState.value.currentLevel == AlertLevel.SOFT) return

        _alertState.value = _alertState.value.copy(
            currentLevel = AlertLevel.SOFT,
            alertMessage = "¿Todo bien? Detectamos un retraso en tu viaje.",
            lastAlertTime = System.currentTimeMillis()
        )

        showSoftNotification()
        Log.d("SafetyAlertEngine", "Alerta suave activada")
    }

    /**
     * NIVEL 2: Alerta activa - countdown 15s
     */
    private fun triggerActiveAlert(
        location: UbicacionUsuario?,
        contacts: List<EmergencyContact>
    ) {
        if (_alertState.value.currentLevel == AlertLevel.ACTIVE && 
            _alertState.value.isCountingDown) return

        _alertState.value = _alertState.value.copy(
            currentLevel = AlertLevel.ACTIVE,
            alertMessage = "¡Desviación detectada! Enviando alerta en 15s...",
            isCountingDown = true,
            countdownSeconds = COUNTDOWN_SECONDS,
            lastAlertTime = System.currentTimeMillis()
        )

        showActiveNotification()
        startCountdown(location, contacts)
        Log.d("SafetyAlertEngine", "Alerta activa iniciada con countdown")
    }

    /**
     * NIVEL 3: Alerta crítica - SMS inmediato
     */
    private fun triggerCriticalAlert(
        location: UbicacionUsuario?,
        contacts: List<EmergencyContact>
    ) {
        _alertState.value = _alertState.value.copy(
            currentLevel = AlertLevel.CRITICAL,
            alertMessage = "¡ALERTA CRÍTICA! Enviando emergencia...",
            isCountingDown = false,
            lastAlertTime = System.currentTimeMillis()
        )

        countdownJob?.cancel()
        showCriticalNotification()
        sendEmergencySms(location, contacts)
        Log.d("SafetyAlertEngine", "Alerta crítica activada - SMS enviado")
    }

    /**
     * Inicia countdown de 15 segundos
     */
    private fun startCountdown(
        location: UbicacionUsuario?,
        contacts: List<EmergencyContact>
    ) {
        countdownJob?.cancel()
        countdownJob = scope.launch(Dispatchers.Main) {
            var seconds = COUNTDOWN_SECONDS
            while (seconds > 0) {
                _alertState.value = _alertState.value.copy(countdownSeconds = seconds)
                updateActiveNotification(seconds)
                delay(1000)
                seconds--
            }

            // Countdown terminado - enviar alerta
            _alertState.value = _alertState.value.copy(isCountingDown = false)
            sendEmergencySms(location, contacts)
        }
    }

    /**
     * Usuario respondió - cancelar alerta activa
     */
    fun userResponded() {
        countdownJob?.cancel()
        _alertState.value = _alertState.value.copy(
            currentLevel = AlertLevel.NONE,
            isCountingDown = false,
            countdownSeconds = 0,
            alertMessage = ""
        )
        notificationManager.cancel(NOTIFICATION_ID_ACTIVE)
        notificationManager.cancel(NOTIFICATION_ID_SOFT)
        Log.d("SafetyAlertEngine", "Usuario respondió - alerta cancelada")
    }

    /**
     * Limpia todas las alertas
     */
    fun clearAllAlerts() {
        countdownJob?.cancel()
        _alertState.value = _alertState.value.copy(
            currentLevel = AlertLevel.NONE,
            isCountingDown = false,
            countdownSeconds = 0,
            alertMessage = ""
        )
        notificationManager.cancel(NOTIFICATION_ID_SOFT)
        notificationManager.cancel(NOTIFICATION_ID_ACTIVE)
        notificationManager.cancel(NOTIFICATION_ID_CRITICAL)
    }

    /**
     * Envía SMS de emergencia a todos los contactos con delivery confirmation
     */
    private fun sendEmergencySms(
        location: UbicacionUsuario?,
        contacts: List<EmergencyContact>
    ) {
        if (contacts.isEmpty()) {
            Log.w("SafetyAlertEngine", "No hay contactos configurados para alerta")
            return
        }

        val message = buildEmergencyMessage(location)

        scope.launch(Dispatchers.IO) {
            var sentCount = 0
            var failedCount = 0

            contacts.forEach { contact ->
                try {
                    val result = smsWrapper.sendWithTracking(contact.phoneNumber, message)
                    if (result.sent) {
                        sentCount++
                        Log.d("SafetyAlertEngine", "SMS enviado a ${contact.name} - Delivered: ${result.delivered}")
                    } else {
                        failedCount++
                        Log.e("SafetyAlertEngine", "SMS falló a ${contact.name}: ${result.failureReason}")
                    }
                } catch (e: Exception) {
                    failedCount++
                    Log.e("SafetyAlertEngine", "Error enviando SMS a ${contact.name}", e)
                }
            }

            _alertState.value = _alertState.value.copy(
                alertMessage = "Alerta enviada a $sentCount contactos${if (failedCount > 0) " ($failedCount fallaron)" else ""}"
            )
        }
    }

    /**
     * Construye mensaje de emergencia
     */
    private fun buildEmergencyMessage(location: UbicacionUsuario?): String {
        val mapsUrl = if (location != null) {
            "https://www.google.com/maps?q=${location.latitud},${location.longitud}"
        } else {
            "Ubicación no disponible"
        }

        return buildString {
            appendLine("🚨 ALERTA DE EMERGENCIA - Regreso Seguro")
            appendLine()
            appendLine("Necesito ayuda urgente.")
            appendLine()
            appendLine("📍 Mi ubicación: $mapsUrl")
            appendLine("⏰ Hora: ${dateFormat.format(Date())}")
            appendLine()
            appendLine("Por favor contáctame.")
        }
    }

    /**
     * Crea canal de notificaciones
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alertas de Seguridad",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas críticas durante el viaje"
            enableVibration(true)
            enableLights(true)
        }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Muestra notificación suave
     */
    private fun showSoftNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("¿Todo bien?")
            .setContentText("Detectamos un retraso en tu viaje")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SOFT, notification)
    }

    /**
     * Muestra notificación activa con countdown
     */
    private fun showActiveNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "respond_alert")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("¡Desviación detectada!")
            .setContentText("Enviando alerta en 15s...")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_ACTIVE, notification)
    }

    /**
     * Actualiza notificación activa con countdown
     */
    private fun updateActiveNotification(seconds: Int) {
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("¡Desviación detectada!")
            .setContentText("Enviando alerta en ${seconds}s...")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_ACTIVE, notification)
    }

    /**
     * Muestra notificación crítica
     */
    private fun showCriticalNotification() {
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("🚨 ALERTA CRÍTICA")
            .setContentText("Alerta de emergencia enviada")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(Notification.PRIORITY_MAX)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(NOTIFICATION_ID_CRITICAL, notification)
    }
}
