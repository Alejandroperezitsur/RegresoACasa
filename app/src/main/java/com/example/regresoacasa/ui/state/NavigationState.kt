package com.example.regresoacasa.ui.state

import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario

/**
 * FASE 1: State Machine para navegación - Reemplaza todos los flags booleanos
 * La UI reacciona SOLO a este estado
 * Renombrado a NavigationFlowState para evitar conflicto con domain.model.NavigationState
 */
sealed class NavigationFlowState {
    object Idle : NavigationFlowState()
    object Searching : NavigationFlowState()
    data class RouteReady(val route: Ruta) : NavigationFlowState()
    data class Navigating(
        val userLocation: UbicacionUsuario,
        val route: Ruta,
        val remainingDistance: Double,
        val remainingDuration: Double
    ) : NavigationFlowState()
    data class Error(val reason: NavigationFlowError) : NavigationFlowState()
    object NoGps : NavigationFlowState()
    object NoInternet : NavigationFlowState()
}

/**
 * Errores de navegación específicos
 */
sealed class NavigationFlowError {
    object ApiKeyInvalid : NavigationFlowError()
    object RouteFailed : NavigationFlowError()
    object GpsDisabled : NavigationFlowError()
    object NoConnection : NavigationFlowError()
    data class Unknown(val message: String) : NavigationFlowError()
}
