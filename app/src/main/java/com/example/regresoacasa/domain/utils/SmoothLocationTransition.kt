package com.example.regresoacasa.domain.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Transición suave entre ubicaciones para evitar saltos bruscos en el mapa.
 * Interpola entre la ubicación anterior y la nueva con animación fluida.
 */
class SmoothLocationTransition(
    private val scope: CoroutineScope,
    private val durationMillis: Int = 300
) {
    private val animatableLat = Animatable(0f)
    private val animatableLon = Animatable(0f)
    private var lastValidLat: Double? = null
    private var lastValidLon: Double? = null
    
    /**
     * Anima la transición a una nueva ubicación.
     * @return Pair<Double, Double> con la ubicación interpolada durante la animación
     */
    suspend fun animateTo(
        targetLat: Double,
        targetLon: Double,
        onUpdate: (Double, Double) -> Unit
    ) {
        val startLat = lastValidLat ?: targetLat
        val startLon = lastValidLon ?: targetLon
        
        // Si la diferencia es muy grande (>100m), saltar directo (snap inicial)
        val distance = calculateDistance(startLat, startLon, targetLat, targetLon)
        
        if (distance > 100 || lastValidLat == null) {
            // Salto inicial o distancia muy grande - sin animación
            lastValidLat = targetLat
            lastValidLon = targetLon
            onUpdate(targetLat, targetLon)
            return
        }
        
        // Animación suave para cambios normales
        animatableLat.snapTo(startLat.toFloat())
        animatableLon.snapTo(startLon.toFloat())
        
        val latJob = scope.launch {
            animatableLat.animateTo(
                targetValue = targetLat.toFloat(),
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = FastOutSlowInEasing
                )
            )
        }
        
        val lonJob = scope.launch {
            animatableLon.animateTo(
                targetValue = targetLon.toFloat(),
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = FastOutSlowInEasing
                )
            )
        }
        
        // Emitir valores durante la animación
        while (latJob.isActive || lonJob.isActive) {
            onUpdate(
                animatableLat.value.toDouble(),
                animatableLon.value.toDouble()
            )
            kotlinx.coroutines.delay(16) // ~60fps
        }
        
        // Valor final exacto
        lastValidLat = targetLat
        lastValidLon = targetLon
        onUpdate(targetLat, targetLon)
    }
    
    /**
     * Versión síncrona para usar sin corutinas (última ubicación conocida)
     */
    fun getSmoothedLocation(targetLat: Double, targetLon: Double): Pair<Double, Double> {
        val startLat = lastValidLat ?: targetLat
        val startLon = lastValidLon ?: targetLon
        
        val distance = calculateDistance(startLat, startLon, targetLat, targetLon)
        
        return if (distance > 100 || lastValidLat == null) {
            lastValidLat = targetLat
            lastValidLon = targetLon
            Pair(targetLat, targetLon)
        } else {
            // Interpolación simple sin animación
            val factor = 0.3f // Suavizado parcial
            val smoothedLat = startLat + (targetLat - startLat) * factor
            val smoothedLon = startLon + (targetLon - startLon) * factor
            
            lastValidLat = smoothedLat
            lastValidLon = smoothedLon
            
            Pair(smoothedLat, smoothedLon)
        }
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000 // Radio de la Tierra en metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
    
    fun reset() {
        lastValidLat = null
        lastValidLon = null
    }
}
