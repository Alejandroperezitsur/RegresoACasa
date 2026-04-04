package com.example.regresoacasa.domain.model

/**
 * Estado completo de navegación en tiempo real
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
    val lastRecalculation: Long = 0
)
