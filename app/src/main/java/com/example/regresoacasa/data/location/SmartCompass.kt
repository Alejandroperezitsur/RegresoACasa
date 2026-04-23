package com.example.regresoacasa.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs

/**
 * FASE 9: Brújula real - Bearing GPS + Sensor fallback
 * Usa bearing GPS cuando hay movimiento, fallback a sensor si está quieto
 */
class SmartCompass(context: Context) : SensorEventListener {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    
    private var currentBearing = 0f
    private var lastGpsBearing: Float? = null
    private var lastGpsBearingTime: Long = 0
    private val GPS_BEARING_VALIDITY_MS = 5000L // GPS bearing válido por 5s
    
    var onBearingChanged: ((Float) -> Unit)? = null
    
    fun start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }
    
    fun stop() {
        sensorManager.unregisterListener(this)
    }
    
    /**
     * Actualiza bearing GPS cuando hay movimiento
     * @param bearing Bearing del GPS en grados (0-360)
     * @param speed Velocidad en m/s
     */
    fun updateGpsBearing(bearing: Float, speed: Float) {
        // Solo usar GPS bearing si hay movimiento significativo (> 2 m/s)
        if (speed > 2.0f && bearing >= 0) {
            lastGpsBearing = bearing
            lastGpsBearingTime = System.currentTimeMillis()
            currentBearing = bearing
            onBearingChanged?.invoke(bearing)
            Log.d("SmartCompass", "Using GPS bearing: $bearing° (speed: $speed m/s)")
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> gravity = it.values.clone()
                Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = it.values.clone()
            }
            
            // Calcular bearing del sensor si tenemos ambos datos
            if (gravity != null && geomagnetic != null) {
                val r = FloatArray(9)
                val i = FloatArray(9)
                
                if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(r, orientation)
                    
                    val sensorBearing = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    val normalizedBearing = (sensorBearing + 360) % 360
                    
                    // Usar sensor solo si GPS bearing expiró o no está disponible
                    val now = System.currentTimeMillis()
                    if (lastGpsBearing == null || (now - lastGpsBearingTime) > GPS_BEARING_VALIDITY_MS) {
                        // Suavizar transición
                        currentBearing = lerpBearing(currentBearing, normalizedBearing, 0.1f)
                        onBearingChanged?.invoke(currentBearing)
                    }
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
    
    /**
     * Interpolación lineal de bearing (maneja el wrap-around 360->0)
     */
    private fun lerpBearing(from: Float, to: Float, factor: Float): Float {
        val diff = to - from
        val adjustedDiff = when {
            diff > 180f -> diff - 360f
            diff < -180f -> diff + 360f
            else -> diff
        }
        val result = from + adjustedDiff * factor
        return (result + 360) % 360
    }
    
    fun getCurrentBearing(): Float = currentBearing
}
