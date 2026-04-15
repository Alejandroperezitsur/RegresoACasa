package com.example.regresoacasa.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

/**
 * Estrategia de ubicación adaptativa que ajusta la frecuencia según la velocidad
 * Optimiza consumo de batería
 */
class AdaptiveLocationStrategy(private val context: Context) {
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    init {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }
    
    /**
     * Calcula el intervalo óptimo basado en la velocidad actual
     * @param speedMetersPerSecond Velocidad actual en m/s
     * @return Intervalo en milisegundos
     */
    fun getInterval(speedMetersPerSecond: Float): Long {
        return when {
            speedMetersPerSecond < 0.5f -> 5000L   // Parado o caminando muy lento: 5s
            speedMetersPerSecond < 4.0f -> 3000L   // Caminando: 3s
            speedMetersPerSecond < 15.0f -> 2000L  // Bicicleta/auto lento: 2s
            else -> 1000L                          // Auto rápido: 1s
        }
    }
    
    /**
     * Crea un LocationRequest con prioridad adaptativa
     */
    @SuppressLint("MissingPermission")
    fun createLocationRequest(speedMetersPerSecond: Float): LocationRequest {
        val interval = getInterval(speedMetersPerSecond)
        
        val priority = when {
            speedMetersPerSecond < 0.5f -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            speedMetersPerSecond < 15.0f -> Priority.PRIORITY_HIGH_ACCURACY
            else -> Priority.PRIORITY_HIGH_ACCURACY
        }
        
        return LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .setMaxUpdateDelayMillis(interval * 2)
            .setWaitForAccurateLocation(speedMetersPerSecond > 10.0f)
            .build()
    }
    
    /**
     * Flujo de ubicaciones con estrategia adaptativa
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(speedMetersPerSecond: Float = 0f): Flow<Location> = callbackFlow {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) 
            != Manifest.permission.GRANTED) {
            close()
            return@callbackFlow
        }
        
        val request = createLocationRequest(speedMetersPerSecond)
        
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location)
                }
            }
        }
        
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        
        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
    
    /**
     * Obtiene la última ubicación conocida
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }
}

// Extensión para convertir Task a suspend function
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return kotlinx.coroutines.tasks.await(this)
}
