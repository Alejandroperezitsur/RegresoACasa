package com.example.regresoacasa.data.safety

import android.content.Context
import android.content.Intent
import android.util.Log
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
import java.util.UUID

/**
 * LiveTrackingSession - Compartir ubicación en tiempo real con contactos
 * Genera link de seguimiento y actualiza ubicación periódicamente
 */
class LiveTrackingSession(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _sessionState = MutableStateFlow<SessionState?>(null)
    val sessionState = _sessionState.asStateFlow()

    private var updateJob: Job? = null
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val updateIntervalMs = 30000L // Actualizar cada 30 segundos

    data class SessionState(
        val sessionId: String,
        val isActive: Boolean,
        val startTime: Long,
        val currentLocation: UbicacionUsuario?,
        val destination: String,
        val etaMinutes: Int,
        val status: TrackingStatus,
        val shareableLink: String,
        val lastUpdateTime: Long
    )

    enum class TrackingStatus {
        ACTIVE,
        ARRIVED,
        ALERT,
        OFFLINE
    }

    /**
     * Inicia una sesión de tracking en vivo
     */
    fun startSession(
        destination: String,
        etaMinutes: Int,
        contacts: List<EmergencyContact>
    ): String {
        val sessionId = UUID.randomUUID().toString().substring(0, 8)
        val shareableLink = generateShareableLink(sessionId)

        _sessionState.value = SessionState(
            sessionId = sessionId,
            isActive = true,
            startTime = System.currentTimeMillis(),
            currentLocation = null,
            destination = destination,
            etaMinutes = etaMinutes,
            status = TrackingStatus.ACTIVE,
            shareableLink = shareableLink,
            lastUpdateTime = System.currentTimeMillis()
        )

        // Enviar notificación inicial a contactos
        sendInitialNotification(contacts, shareableLink, etaMinutes)

        // Iniciar actualizaciones periódicas
        startPeriodicUpdates(contacts)

        Log.d("LiveTrackingSession", "Sesión iniciada: $sessionId")
        return shareableLink
    }

    /**
     * Actualiza la ubicación actual
     */
    fun updateLocation(location: UbicacionUsuario) {
        val currentState = _sessionState.value ?: return
        if (!currentState.isActive) return

        _sessionState.value = currentState.copy(
            currentLocation = location,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * Actualiza el estado del tracking
     */
    fun updateStatus(status: TrackingStatus) {
        val currentState = _sessionState.value ?: return
        _sessionState.value = currentState.copy(status = status)
    }

    /**
     * Detiene la sesión de tracking
     */
    fun stopSession(contacts: List<EmergencyContact>) {
        updateJob?.cancel()
        val currentState = _sessionState.value ?: return

        // Enviar notificación de llegada
        if (currentState.status == TrackingStatus.ACTIVE) {
            sendArrivalNotification(contacts)
        }

        _sessionState.value = currentState.copy(
            isActive = false,
            status = TrackingStatus.ARRIVED
        )

        Log.d("LiveTrackingSession", "Sesión detenida: ${currentState.sessionId}")
    }

    /**
     * Inicia actualizaciones periódicas de ubicación
     */
    private fun startPeriodicUpdates(contacts: List<EmergencyContact>) {
        updateJob?.cancel()
        updateJob = scope.launch(Dispatchers.Default) {
            while (_sessionState.value?.isActive == true) {
                delay(updateIntervalMs)
                val state = _sessionState.value ?: break
                if (state.isActive && state.currentLocation != null) {
                    sendLocationUpdate(contacts, state)
                }
            }
        }
    }

    /**
     * Genera link compartible (mock - en producción sería un link real)
     */
    private fun generateShareableLink(sessionId: String): String {
        // En producción, esto sería un link real a un servidor
        // Por ahora usamos un link de Google Maps con el session ID como referencia
        return "https://regresoseguro.app/track/$sessionId"
    }

    /**
     * Envía notificación inicial a contactos
     */
    private fun sendInitialNotification(
        contacts: List<EmergencyContact>,
        link: String,
        etaMinutes: Int
    ) {
        val message = buildString {
            appendLine("🛡️ Regreso Seguro Activado")
            appendLine()
            appendLine("Te estoy compartiendo mi ubicación en tiempo real.")
            appendLine()
            appendLine("🔗 Seguimiento: $link")
            appendLine("⏰ ETA: ~$etaMinutes minutos")
            appendLine()
            appendLine("Te aviso cuando llegue 👍")
        }

        contacts.forEach { contact ->
            sendSmsToContact(contact, message)
        }
    }

    /**
     * Envía actualización de ubicación
     */
    private fun sendLocationUpdate(
        contacts: List<EmergencyContact>,
        state: SessionState
    ) {
        val location = state.currentLocation ?: return
        val mapsUrl = "https://www.google.com/maps?q=${location.latitud},${location.longitud}"

        val message = buildString {
            appendLine("🛡️ Actualización de Regreso Seguro")
            appendLine()
            appendLine("📍 Ubicación: $mapsUrl")
            appendLine("🔗 Seguimiento: ${state.shareableLink}")
            appendLine("⏰ Hora: ${dateFormat.format(Date())}")
        }

        contacts.forEach { contact ->
            sendSmsToContact(contact, message)
        }

        Log.d("LiveTrackingSession", "Actualización enviada a ${contacts.size} contactos")
    }

    /**
     * Envía notificación de llegada
     */
    private fun sendArrivalNotification(contacts: List<EmergencyContact>) {
        val message = buildString {
            appendLine("✅ Llegué bien a mi destino")
            appendLine()
            appendLine("Gracias por estar pendiente 👍")
            appendLine("⏰ Hora: ${dateFormat.format(Date())}")
        }

        contacts.forEach { contact ->
            sendSmsToContact(contact, message)
        }

        Log.d("LiveTrackingSession", "Notificación de llegada enviada")
    }

    /**
     * Envía SMS a un contacto específico
     */
    private fun sendSmsToContact(contact: EmergencyContact, message: String) {
        try {
            val smsManager = context.getSystemService(android.telephony.SmsManager::class.java)
            smsManager.sendTextMessage(
                contact.phoneNumber,
                null,
                message,
                null,
                null
            )
        } catch (e: Exception) {
            Log.e("LiveTrackingSession", "Error enviando SMS a ${contact.name}", e)
        }
    }

    /**
     * Obtiene el tiempo transcurrido en minutos
     */
    fun getElapsedMinutes(): Int {
        val state = _sessionState.value ?: return 0
        return ((System.currentTimeMillis() - state.startTime) / 1000 / 60).toInt()
    }

    /**
     * Verifica si la sesión está activa
     */
    fun isSessionActive(): Boolean {
        return _sessionState.value?.isActive == true
    }
}
