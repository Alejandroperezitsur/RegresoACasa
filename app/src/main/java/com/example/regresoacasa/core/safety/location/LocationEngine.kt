package com.example.regresoacasa.core.safety.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.regresoacasa.core.safety.SafetyConstants
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

/**
 * Motor de Ubicación Fault-Tolerant
 * 
 * Reemplaza el sistema actual de tracking con un motor que:
 * - Detecta pérdida de señal
 * - Detecta baja precisión
 * - Detecta saltos irreales (spoofing)
 * - NUNCA detiene el sistema, degrada gracefulmente
 * - Emite estados degradados cuando GPS falla
 */
class LocationEngine(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    // V3: SpoofingDetector para análisis avanzado de GPS
    private val spoofingDetector = SpoofingDetector()
    
    private var locationCallback: LocationCallback? = null
    private var isTracking = false
    
    // Estado interno del motor
    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle as EngineState)
    val engineState: StateFlow<EngineState> = _engineState
    
    // Última ubicación conocida confiable
    private var lastReliableLocation: Location? = null
    
    // Historial para detección de anomalías
    private val locationHistory = mutableListOf<Location>()
    private val maxHistorySize = 10
    
    // Contador de ubicaciones degradadas consecutivas
    private var degradedCount = 0
    
    /**
     * Estado del motor de ubicación
     */
    sealed class EngineState {
        object Idle : EngineState()
        object Tracking : EngineState()
        data class Degraded(val reason: String) : EngineState()
        data class Error(val error: String) : EngineState()
        
        companion object {
            fun idle() = Idle
            fun tracking() = Tracking
            fun degraded(reason: String) = Degraded(reason)
            fun error(error: String) = Error(error)
        }
    }
    
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
    
    /**
     * Inicia el tracking de ubicación
     * 
     * @param intervalMillis Intervalo entre actualizaciones (default: 3000ms)
     * @return Flow de LocationUpdate
     */
    fun start(intervalMillis: Long = 3000L): Flow<LocationUpdate> = callbackFlow {
        
        if (!hasLocationPermission()) {
            close(Exception("Location permission required"))
            return@callbackFlow
        }
        
        isTracking = true
        _engineState.value = EngineState.Tracking
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMillis
        ).apply {
            setMinUpdateIntervalMillis(intervalMillis / 2)
            setWaitForAccurateLocation(true)
        }.build()
        
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    processLocation(location)?.let { update ->
                        trySend(update)
                    }
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
            val lastKnown = lastReliableLocation
            _engineState.value = EngineState.Error("Permission revoked")
            trySend(LocationUpdate.Error("Permission revoked", lastKnown))
            close(e)
        } catch (e: Exception) {
            val lastKnown = lastReliableLocation
            _engineState.value = EngineState.Error(e.message ?: "Unknown error")
            trySend(LocationUpdate.Error(e.message ?: "Unknown error", lastKnown))
            close(e)
        }
        
        awaitClose {
            stop()
        }
    }
    
    /**
     * Detiene el tracking de ubicación
     */
    fun stop() {
        isTracking = false
        locationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                // Log error but don't crash
            }
        }
        locationCallback = null
        _engineState.value = EngineState.Idle
    }
    
    /**
     * Obtiene la última ubicación conocida
     */
    fun getLastKnown(): Location? = lastReliableLocation
    
    /**
     * Procesa una ubicación y determina si es confiable
     */
    private fun processLocation(location: Location): LocationUpdate? {
        // Agregar al historial
        locationHistory.add(location)
        if (locationHistory.size > maxHistorySize) {
            locationHistory.removeAt(0)
        }
        
        // V3: Usar SpoofingDetector para análisis avanzado
        val spoofingScore = spoofingDetector.analyzeLocation(
            location = location,
            locationHistory = locationHistory,
            lastReliableLocation = lastReliableLocation
        )
        
        if (spoofingScore.isSpoofed) {
            _engineState.value = EngineState.Degraded("GPS spoofing detected (score: ${spoofingScore.totalScore})")
            degradedCount++
            
            if (degradedCount >= SafetyConstants.SPOOFING_DETECTION_THRESHOLD) {
                // Demasiados intentos de spoofing, usar última ubicación confiable
                return lastReliableLocation?.let { 
                    LocationUpdate.Degraded(it, "GPS spoofing detected - using last known location")
                }
            }
            return null // Ignorar esta ubicación
        }
        
        // Detectar salto irreales
        if (isUnrealisticJump(location)) {
            _engineState.value = EngineState.Degraded("Unrealistic location jump detected")
            return lastReliableLocation?.let { 
                LocationUpdate.Degraded(it, "Unrealistic jump - using last known location")
            }
        }
        
        // Evaluar precisión
        val accuracy = location.accuracy
        val isReliable = when {
            accuracy < SafetyConstants.GPS_PRECISION_NAVIGABLE -> true
            accuracy < SafetyConstants.GPS_PRECISION_TRACKING -> true
            accuracy < SafetyConstants.GPS_PRECISION_DEGRADED -> {
                // Precisión degradada pero usable
                degradedCount++
                if (degradedCount >= SafetyConstants.DEGRADED_GPS_COUNT_THRESHOLD) {
                    _engineState.value = EngineState.Degraded("Low GPS accuracy")
                }
                true
            }
            else -> {
                // Precisión muy pobre
                _engineState.value = EngineState.Degraded("Very low GPS accuracy")
                return lastReliableLocation?.let { 
                    LocationUpdate.Degraded(it, "GPS accuracy too low - using last known location")
                }
            }
        }
        
        // Si es confiable, actualizar última ubicación y resetear contador
        if (isReliable) {
            lastReliableLocation = location
            degradedCount = 0
            _engineState.value = EngineState.Tracking
            
            return LocationUpdate.Reliable(
                location = location,
                accuracy = accuracy,
                isMock = isMockLocation(location)
            )
        }
        
        return null
    }
    
    /**
     * Detecta si la ubicación es spoofed (falsificada)
     */
    private fun isSpoofedLocation(location: Location): Boolean {
        // Verificar si el provider indica que es mock
        if (isMockLocation(location)) {
            return true
        }
        
        // Verificar consistencia con historial
        if (locationHistory.size >= 2) {
            val prev = locationHistory[locationHistory.size - 2]
            val timeDiff = location.time - prev.time
            val distance = distanceBetween(prev, location)
            
            if (timeDiff > 0) {
                val velocity = distance / timeDiff // m/s
                if (velocity > SafetyConstants.MAX_REALISTIC_VELOCITY_MS) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Detecta saltos irreales de ubicación
     */
    private fun isUnrealisticJump(location: Location): Boolean {
        val lastKnown = lastReliableLocation ?: return false
        
        val timeDiff = location.time - lastKnown.time
        val distance = distanceBetween(lastKnown, location)
        
        // Si el salto es > 1km en < 10 segundos, es irreales
        if (distance > 1000 && timeDiff < 10000) {
            return true
        }
        
        return false
    }
    
    /**
     * Verifica si la ubicación es mock (falsificada por el sistema)
     */
    private fun isMockLocation(location: Location): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            location.isFromMockProvider
        } else {
            false
        }
    }
    
    /**
     * Calcula distancia entre dos ubicaciones en metros
     */
    private fun distanceBetween(loc1: Location, loc2: Location): Float {
        return loc1.distanceTo(loc2)
    }
    
    /**
     * Verifica si hay permisos de ubicación
     */
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
}
