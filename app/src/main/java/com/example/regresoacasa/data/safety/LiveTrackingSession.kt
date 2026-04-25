package com.example.regresoacasa.data.safety

import android.content.Context
import android.util.Log
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
import java.util.UUID

/**
 * LiveTrackingSession - Compartir ubicación en tiempo real con contactos
 * Genera link de seguimiento y actualiza ubicación periódicamente
 */
class LiveTrackingSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val smsWrapper: SmsManagerWrapper
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
        val lastUpdateTime: Long,
        val gpsAccuracy: Float,
        val currentSpeed: Float,
        val batteryLevel: Int,
        val signalQuality: SignalQuality,
        val offlineEvents: List<OfflineEvent>
    )

    enum class TrackingStatus {
        ACTIVE,
        ARRIVED,
        ALERT,
        CRITICAL,
        OFFLINE
    }

    enum class SignalQuality {
        EXCELLENT,
        GOOD,
        FAIR,
        POOR,
        NONE
    }

    data class OfflineEvent(
        val timestamp: Long,
        val location: UbicacionUsuario?,
        val eventType: String,
        val data: String
    )

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
            lastUpdateTime = System.currentTimeMillis(),
            gpsAccuracy = 0f,
            currentSpeed = 0f,
            batteryLevel = 100,
            signalQuality = SignalQuality.GOOD,
            offlineEvents = emptyList()
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
            lastUpdateTime = System.currentTimeMillis(),
            gpsAccuracy = location.precision ?: 0f
        )
    }

    /**
     * Actualiza métricas adicionales
     */
    fun updateMetrics(speed: Float, batteryLevel: Int, signalQuality: SignalQuality) {
        val currentState = _sessionState.value ?: return
        if (!currentState.isActive) return

        _sessionState.value = currentState.copy(
            currentSpeed = speed,
            batteryLevel = batteryLevel,
            signalQuality = signalQuality
        )
    }

    /**
     * Agrega un evento offline
     */
    fun addOfflineEvent(eventType: String, data: String, location: UbicacionUsuario? = null) {
        val currentState = _sessionState.value ?: return
        if (!currentState.isActive) return

        val newEvent = OfflineEvent(
            timestamp = System.currentTimeMillis(),
            location = location,
            eventType = eventType,
            data = data
        )

        _sessionState.value = currentState.copy(
            offlineEvents = currentState.offlineEvents + newEvent
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
     * Genera link compartible - DECISIÓN: Usar Google Maps directo en lugar de backend falso
     * Live tracking real requiere backend completo. Por ahora, SMS con coordenadas es suficiente para MVP.
     */
    private fun generateShareableLink(sessionId: String): String {
        // Retorna string descriptivo - el link real se genera en sendLocationUpdate con coordenadas
        return "SMS con ubicación en tiempo real"
    }

    /**
     * Envía notificación inicial a contactos
     * DECISIÓN: Usar coordenadas directas de Google Maps en lugar de link falso
     */
    private fun sendInitialNotification(
        contacts: List<EmergencyContact>,
        link: String,
        etaMinutes: Int
    ) {
        val state = _sessionState.value
        val location = state?.currentLocation
        
        val message = buildString {
            appendLine("🛡️ Regreso Seguro Activado")
            appendLine()
            appendLine("Te estoy compartiendo mi ubicación en tiempo real.")
            appendLine()
            if (location != null) {
                val mapsUrl = "https://www.google.com/maps?q=${location.latitud},${location.longitud}"
                appendLine("� Mi ubicación: $mapsUrl")
            }
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
     * DECISIÓN: Usar solo coordenadas de Google Maps, eliminar link falso
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
            appendLine("📍 Mi ubicación: $mapsUrl")
            appendLine("⏰ Hora: ${dateFormat.format(Date())}")
            appendLine("📡 Señal: ${state.signalQuality}")
            appendLine("🔋 Batería: ${state.batteryLevel}%")
        }

        contacts.forEach { contact ->
            sendSmsToContact(contact, message)
        }

        Log.d("LiveTrackingSession", "Actualización enviada a ${contacts.size} contactos")
    }

    /**
     * Envía resumen de eventos offline cuando se reconecta
     */
    fun sendOfflineSummary(contacts: List<EmergencyContact>) {
        val state = _sessionState.value ?: return
        if (state.offlineEvents.isEmpty()) return

        val message = buildString {
            appendLine("🛡️ Resumen de Regreso Seguro (Offline)")
            appendLine()
            appendLine("Se restauró la conexión. Eventos durante el periodo offline:")
            appendLine()
            state.offlineEvents.forEach { event ->
                appendLine("• ${event.eventType}: ${event.data}")
                appendLine("  Hora: ${dateFormat.format(Date(event.timestamp))}")
                if (event.location != null) {
                    appendLine("  Ubicación: https://www.google.com/maps?q=${event.location.latitud},${event.location.longitud}")
                }
                appendLine()
            }
            appendLine("📍 Ubicación actual: ${state.currentLocation?.let { "https://www.google.com/maps?q=${it.latitud},${it.longitud}" } ?: "No disponible"}")
            appendLine("🔗 Seguimiento: ${state.shareableLink}")
        }

        contacts.forEach { contact ->
            sendSmsToContact(contact, message)
        }

        Log.d("LiveTrackingSession", "Resumen offline enviado a ${contacts.size} contactos")
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
     * Envía SMS a un contacto específico con delivery confirmation
     */
    private fun sendSmsToContact(contact: EmergencyContact, message: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val result = smsWrapper.sendWithTracking(contact.phoneNumber, message)
                Log.d("LiveTrackingSession", "SMS enviado a ${contact.name} - Sent: ${result.sent}, Delivered: ${result.delivered}")
            } catch (e: Exception) {
                Log.e("LiveTrackingSession", "Error enviando SMS a ${contact.name}", e)
            }
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
