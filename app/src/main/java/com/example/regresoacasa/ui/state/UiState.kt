package com.example.regresoacasa.ui.state

import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.NavigationState
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario

/**
 * Estados UI sealed class para manejo robusto de estados
 */
sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val data: Any? = null) : UiState()
    data class Error(val message: String, val retryable: Boolean = true) : UiState()
}

/**
 * Estados específicos de navegación
 */
sealed class NavigationUiState {
    object Idle : NavigationUiState()
    object Calculating : NavigationUiState()
    data class Navigating(
        val navigationState: NavigationState
    ) : NavigationUiState()
    data class OffRoute(
        val lastKnownLocation: UbicacionUsuario,
        val recalculating: Boolean = false
    ) : NavigationUiState()
    data class Error(
        val message: String,
        val type: ErrorType
    ) : NavigationUiState()
}

enum class ErrorType {
    NO_INTERNET,
    GPS_DISABLED,
    API_ERROR,
    TIMEOUT,
    UNKNOWN
}

/**
 * Estado completo de la UI principal
 */
data class MainUiState(
    val pantallaActual: Pantalla = Pantalla.MAP,
    val uiState: UiState = UiState.Idle,
    val navigationUiState: NavigationUiState = NavigationUiState.Idle,
    
    // Datos
    val ubicacionActual: UbicacionUsuario? = null,
    val casa: LugarFavorito? = null,
    val rutaActual: Ruta? = null,
    val navigationState: NavigationState = NavigationState(),
    
    // Búsqueda
    val busqueda: String = "",
    val resultadosBusqueda: List<Lugar> = emptyList(),
    val lugarSeleccionado: Lugar? = null,
    
    // Flags
    val estaCargandoUbicacion: Boolean = false,
    val estaCalculandoRuta: Boolean = false,
    val estaBuscando: Boolean = false,
    val estaGuardando: Boolean = false,
    val isTrackingLocation: Boolean = false
)

enum class Pantalla {
    MAP,
    SEARCH,
    NAVEGACION,
    FAVORITES
}
