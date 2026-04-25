package com.example.regresoacasa.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt
import com.example.regresoacasa.core.GpsStatus

class LocationOrchestrator(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private val _gpsStatus = MutableStateFlow<GpsStatus>(GpsStatus.Lost)
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()
    
    private val _currentSpeedKmh = MutableStateFlow(0f)
    val currentSpeedKmh: StateFlow<Float> = _currentSpeedKmh.asStateFlow()
    
    private var locationCallback: LocationCallback? = null
    private var isTracking = false
    private var restartTrackingCallback: (() -> Unit)? = null
    
    private val locationHistory = mutableListOf<Location>()
    private val maxHistorySize = 20
    
    fun startTracking(): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            close(Exception("Location permission required"))
            return@callbackFlow
        }
        
        isTracking = true
        
        fun restartWithNewRequest() {
            locationCallback?.let { callback ->
                try {
                    fusedLocationClient.removeLocationUpdates(callback)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            val locationRequest = createAdaptiveRequest()
            
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        processLocation(location)
                        trySend(location)
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
            } catch (e: Exception) {
                close(e)
            }
        }
        
        restartTrackingCallback = { restartWithNewRequest() }
        
        restartWithNewRequest()
        
        awaitClose {
            stopTracking()
        }
    }
    
    fun stopTracking() {
        isTracking = false
        locationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                // Ignore
            }
        }
        locationCallback = null
    }
    
    private fun createAdaptiveRequest(): LocationRequest {
        val speedKmh = _currentSpeedKmh.value
        val intervalMillis = when {
            speedKmh < 2f -> 10000L // 10s - stationary
            speedKmh < 6f -> 5000L  // 5s - walking
            else -> 2000L           // 2s - vehicle
        }
        
        return LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMillis
        ).apply {
            setMinUpdateIntervalMillis(intervalMillis / 2)
            setWaitForAccurateLocation(true)
            setMaxUpdateDelayMillis(intervalMillis * 2)
        }.build()
    }
    
    private fun processLocation(location: Location) {
        // Update history
        locationHistory.add(location)
        if (locationHistory.size > maxHistorySize) {
            locationHistory.removeAt(0)
        }
        
        // Calculate speed
        val speedKmh = calculateSpeed(location)
        _currentSpeedKmh.value = speedKmh
        
        // Update GPS status based on accuracy
        val accuracy = location.accuracy
        _gpsStatus.value = when {
            accuracy < 10f -> GpsStatus.Excellent
            accuracy < 25f -> GpsStatus.Good
            accuracy < 100f -> GpsStatus.Weak(accuracy)
            else -> GpsStatus.Lost
        }
        
        // If speed changed significantly, restart tracking with new interval
        if (locationHistory.size >= 2) {
            val prevSpeed = calculateSpeed(locationHistory[locationHistory.size - 2])
            if (abs(speedKmh - prevSpeed) > 3f && isTracking) {
                // Speed changed significantly, restart tracking with new interval
                restartTrackingCallback?.invoke()
            }
        }
    }
    
    private fun calculateSpeed(location: Location): Float {
        if (locationHistory.size < 2) return 0f
        
        val prev = locationHistory[locationHistory.size - 2]
        val timeDiff = (location.time - prev.time) / 1000f // seconds
        
        if (timeDiff <= 0) return 0f
        
        val distance = prev.distanceTo(location) // meters
        val speedMs = distance / timeDiff // m/s
        return speedMs * 3.6f // km/h
    }
    
    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
