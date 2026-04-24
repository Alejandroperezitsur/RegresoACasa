package com.example.regresoacasa.data.safety

import android.content.Context
import android.util.Log
import com.example.regresoacasa.core.safety.alert.AlertPersistence
import com.example.regresoacasa.core.safety.alert.PendingAlert
import com.example.regresoacasa.core.safety.alert.SmsManagerWrapper
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.utils.SafeHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * GuardianManager - Módulo de seguridad activa
 * Monitorea el progreso del usuario y gestiona alertas de emergencia.
 */
class GuardianManager(
    private val context: Context,
    private val haptics: SafeHaptics,
    private val smsWrapper: SmsManagerWrapper,
    private val persistence: AlertPersistence
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    private val _isGuardianActive = MutableStateFlow(false)
    val isGuardianActive = _isGuardianActive.asStateFlow()

    private var trustedContactPhone: String? = null
    private var lastKnownLocation: UbicacionUsuario? = null
    private var checkInJob: Job? = null

    fun activateGuardian(phoneNumber: String) {
        trustedContactPhone = phoneNumber
        _isGuardianActive.value = true
        startCheckInTimer()
        Log.d("GuardianManager", "Guardian activado para: $phoneNumber")
    }

    fun deactivateGuardian() {
        _isGuardianActive.value = false
        checkInJob?.cancel()
        Log.d("GuardianManager", "Guardian desactivado")
    }

    fun updateLocation(location: UbicacionUsuario) {
        lastKnownLocation = location
    }

    private fun startCheckInTimer() {
        checkInJob?.cancel()
        checkInJob = scope.launch {
            while (_isGuardianActive.value) {
                delay(300000) // Cada 5 minutos
                haptics.guardianCheckIn()
            }
        }
    }

    fun sendEmergencyAlert() {
        val phone = trustedContactPhone ?: return
        val location = lastKnownLocation
        val message = if (location != null) {
            "¡ALERTA! Regreso a Casa: Necesito ayuda. Mi ubicación: https://www.google.com/maps?q=${location.latitud},${location.longitud}"
        } else {
            "¡ALERTA! Regreso a Casa: Necesito ayuda urgente."
        }

        scope.launch {
            try {
                val result = smsWrapper.sendWithTracking(phone, message)
                haptics.guardianAlert()
                Log.d("GuardianManager", "Alerta de emergencia enviada a $phone - Sent: ${result.sent}, Delivered: ${result.delivered}")
            } catch (e: Exception) {
                Log.e("GuardianManager", "Error enviando SMS", e)
            }
        }
    }
}
