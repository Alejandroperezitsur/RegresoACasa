package com.example.regresoacasa.core.safety.location

import android.location.Location

/**
 * Actualización de ubicación emitida por el motor
 */
sealed class LocationUpdate {
    data class Reliable(
        val location: Location,
        val accuracy: Float,
        val isMock: Boolean
    ) : LocationUpdate()
    
    data class Degraded(
        val lastKnownLocation: Location,
        val reason: String
    ) : LocationUpdate()
    
    data class Error(
        val error: String,
        val lastKnownLocation: Location?
    ) : LocationUpdate()
}
