package com.example.regresoacasa.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Servicio de tracking de ubicación en tiempo real
 */
class LocationTrackingService(
    private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null

    /**
     * Flujo continuo de actualizaciones de ubicación
     */
    fun startLocationUpdates(
        intervalMillis: Long = 3000L,
        fastestIntervalMillis: Long = 2000L
    ): Flow<UbicacionUsuario> = callbackFlow {
        
        if (!hasLocationPermission()) {
            close(Exception("Permiso de ubicación requerido"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMillis
        ).apply {
            setMinUpdateIntervalMillis(fastestIntervalMillis)
            setWaitForAccurateLocation(true)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location.toDomain())
                }
            }
        }

        locationCallback = callback

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            stopLocationUpdates()
        }
    }

    /**
     * Detener actualizaciones
     */
    fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun Location.toDomain(): UbicacionUsuario {
        return UbicacionUsuario(
            latitud = latitude,
            longitud = longitude,
            precision = accuracy,
            timestamp = time
        )
    }
}
