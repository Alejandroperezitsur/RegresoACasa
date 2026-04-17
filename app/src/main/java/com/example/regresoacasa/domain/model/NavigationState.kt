package com.example.regresoacasa.domain.model

import com.example.regresoacasa.ui.state.SystemFeedbackState

/**
 * Estado global de navegación - fuente única de verdad
 * Elimina inconsistencias entre UI, haptics y lógica
 */
enum class NavigationStatus {
    NORMAL,          // Navegando normalmente
    OFF_ROUTE,       // Desviado de la ruta
    RECALCULATING,   // Recalculando ruta
    GPS_WEAK,        // GPS impreciso
    ARRIVING         // Llegando al destino
}

/**
 * Estado completo de navegación en tiempo real v2.0
 * Soporta UI mínima y turn-by-turn navigation
 */
data class NavigationState(
    val userLocation: UbicacionUsuario? = null,
    val route: Ruta? = null,
    val destination: LugarFavorito? = null,
    val remainingDistance: Double = 0.0,
    val remainingDuration: Double = 0.0,
    val transportMode: String = "foot-walking", // "foot-walking", "driving-car", "cycling-regular"
    val eta: String = "",
    val isOffRoute: Boolean = false,
    val distanceToRoute: Double = 0.0,
    val isFollowingUser: Boolean = true,
    val lastRecalculation: Long = 0,
    
    // Campos nuevos para turn-by-turn navigation
    val currentInstruction: Instruccion? = null,
    val distanceToNextTurn: Double = 0.0,
    val progressToNextTurn: Float = 0f,
    val instructions: List<Instruccion> = emptyList(),
    val currentInstructionIndex: Int = 0,
    
    // Campos para detección de llegada
    val hasArrived: Boolean = false,
    val arrivalTime: Long = 0,
    val totalDistance: Double = 0.0,
    val elapsedTime: Long = 0,
    val startTime: Long = 0,
    
    // Estado del sistema de feedback háptico
    val systemFeedback: SystemFeedbackState = SystemFeedbackState.Normal,
    
    // FASE 1: Fuente única de verdad - timing consistente
    val navigationStatus: NavigationStatus = NavigationStatus.NORMAL,
    val distanceToNextTurnStable: Double = 0.0,  // FASE 2: Anti-flicker
    val distanceBucketStable: Int = 999,          // FASE 2: Bucket estable (nunca sube)
    val displayedDistance: Double = 0.0,          // FASE 3: Suavizado visual
    val realDeviation: Double = 0.0               // FASE 4: Desviación real vs snap
)
