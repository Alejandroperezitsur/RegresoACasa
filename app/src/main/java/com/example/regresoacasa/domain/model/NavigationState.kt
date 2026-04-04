package com.example.regresoacasa.domain.model

import com.example.regresoacasa.ui.state.SystemFeedbackState

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
    val isOffRoute: Boolean = false,
    val distanceToRoute: Double = 0.0,
    val isFollowingUser: Boolean = true,
    val lastRecalculation: Long = 0,
    
    // Campos nuevos para turn-by-turn navigation
    val currentInstruction: InstruccionNavegacion? = null,
    val distanceToNextTurn: Double = 0.0,
    val progressToNextTurn: Float = 0f,
    val instructions: List<InstruccionNavegacion> = emptyList(),
    val currentInstructionIndex: Int = 0,
    
    // Campos para detección de llegada
    val hasArrived: Boolean = false,
    val arrivalTime: Long = 0,
    val totalDistance: Double = 0.0,
    val elapsedTime: Long = 0,
    val startTime: Long = 0,
    
    // Estado del sistema de feedback háptico
    val systemFeedback: SystemFeedbackState = SystemFeedbackState.Normal
)
