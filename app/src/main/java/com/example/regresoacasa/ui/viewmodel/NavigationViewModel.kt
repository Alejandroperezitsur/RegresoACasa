package com.example.regresoacasa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.regresoacasa.di.AppModule
import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.NavigationState
import com.example.regresoacasa.domain.model.PuntoRuta
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.usecase.BuscarLugaresUseCase
import com.example.regresoacasa.domain.usecase.CalcularRutaUseCase
import com.example.regresoacasa.domain.usecase.GuardarFavoritoUseCase
import com.example.regresoacasa.domain.usecase.ObtenerCasaUseCase
import com.example.regresoacasa.ui.state.ErrorType
import com.example.regresoacasa.ui.state.MainUiState
import com.example.regresoacasa.ui.state.NavigationUiState
import com.example.regresoacasa.ui.state.Pantalla
import com.example.regresoacasa.ui.state.UiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(FlowPreview::class)
class NavigationViewModel(
    private val appModule: AppModule
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private var locationTrackingJob: Job? = null
    private var routeCalculationJob: Job? = null
    private val routeMutex = Mutex()
    
    // Caché de última ruta
    private var lastRouteCalculation: Long = 0
    private val MIN_RECALCULATION_INTERVAL = 10000L // 10 segundos mínimo entre recálculos

    init {
        cargarCasa()
        setupSearchDebounce()
    }

    private fun setupSearchDebounce() {
        _searchQuery
            .debounce(400)
            .onEach { query ->
                if (query.length >= 3) {
                    buscarLugares(query)
                } else {
                    _uiState.update { it.copy(resultadosBusqueda = emptyList()) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(busqueda = query) }
    }

    private fun buscarLugares(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(estaBuscando = true, uiState = UiState.Loading) }
            
            try {
                appModule.buscarLugaresUseCase(query).collect { result ->
                    result.fold(
                        onSuccess = { lugares ->
                            _uiState.update {
                                it.copy(
                                    resultadosBusqueda = lugares,
                                    estaBuscando = false,
                                    uiState = UiState.Success()
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    error = "Error al buscar: ${error.message}",
                                    estaBuscando = false,
                                    uiState = UiState.Error(error.message ?: "Error desconocido")
                                )
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Error de red: ${e.message}",
                        estaBuscando = false,
                        uiState = UiState.Error("Sin conexión", false)
                    )
                }
            }
        }
    }

    fun cargarCasa() {
        viewModelScope.launch {
            try {
                val casa = appModule.obtenerCasaUseCase()
                _uiState.update { it.copy(casa = casa) }
            } catch (e: Exception) {
                // Casa no configurada es estado válido
            }
        }
    }

    fun obtenerUbicacionUnica() {
        viewModelScope.launch {
            _uiState.update { it.copy(estaCargandoUbicacion = true, uiState = UiState.Loading) }
            
            try {
                appModule.obtenerUbicacionUseCase().fold(
                    onSuccess = { ubicacion ->
                        _uiState.update {
                            it.copy(
                                ubicacionActual = ubicacion,
                                estaCargandoUbicacion = false,
                                uiState = UiState.Success()
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                error = error.message ?: "Error al obtener ubicación",
                                estaCargandoUbicacion = false,
                                uiState = UiState.Error(error.message ?: "Error GPS")
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "GPS no disponible",
                        estaCargandoUbicacion = false,
                        uiState = UiState.Error("GPS desactivado", false)
                    )
                }
            }
        }
    }

    fun iniciarNavegacion(modo: String = "foot-walking") {
        val casa = _uiState.value.casa ?: run {
            _uiState.update { 
                it.copy(
                    uiState = UiState.Error("Configura tu casa primero", false),
                    error = "Configura tu casa primero"
                ) 
            }
            return
        }

        // Iniciar tracking continuo
        iniciarTrackingContinuo()

        // Calcular ruta inicial
        calcularRuta(casa, modo)
    }

    private fun iniciarTrackingContinuo() {
        // Cancelar tracking anterior si existe
        locationTrackingJob?.cancel()

        locationTrackingJob = viewModelScope.launch {
            _uiState.update { it.copy(isTrackingLocation = true) }
            
            try {
                appModule.locationTrackingService
                    .startLocationUpdates(intervalMillis = 3000, fastestIntervalMillis = 2000)
                    .catch { e ->
                        _uiState.update {
                            it.copy(
                                uiState = UiState.Error("Error de ubicación: ${e.message}"),
                                isTrackingLocation = false
                            )
                        }
                    }
                    .collect { ubicacion ->
                        actualizarUbicacionEnNavegacion(ubicacion)
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        uiState = UiState.Error("Permiso de ubicación requerido", false),
                        isTrackingLocation = false
                    )
                }
            }
        }
    }

    private fun actualizarUbicacionEnNavegacion(ubicacion: UbicacionUsuario) {
        val currentState = _uiState.value
        val ruta = currentState.rutaActual ?: return

        // Calcular distancia a la ruta
        val distanciaARuta = calcularDistanciaARuta(ubicacion, ruta)
        val isOffRoute = distanciaARuta > 50 // 50 metros de tolerancia

        // Calcular distancia restante (aproximada)
        val remainingDistance = calcularDistanciaRestante(ubicacion, ruta)
        val remainingDuration = (remainingDistance / ruta.distanciaMetros) * ruta.duracionSegundos

        // Actualizar estado de navegación
        val navigationState = NavigationState(
            userLocation = ubicacion,
            route = ruta,
            destination = currentState.casa,
            remainingDistance = remainingDistance,
            remainingDuration = remainingDuration,
            isOffRoute = isOffRoute,
            distanceToRoute = distanciaARuta,
            isFollowingUser = currentState.navigationState.isFollowingUser,
            lastRecalculation = currentState.navigationState.lastRecalculation
        )

        _uiState.update {
            it.copy(
                ubicacionActual = ubicacion,
                navigationState = navigationState,
                navigationUiState = when {
                    isOffRoute && !currentState.estaCalculandoRuta -> 
                        NavigationUiState.OffRoute(ubicacion, false)
                    else -> NavigationUiState.Navigating(navigationState)
                }
            )
        }

        // Re-routing automático si está muy desviado
        if (isOffRoute && distanciaARuta > 100) {
            recalcularRutaSiEsNecesario()
        }
    }

    private fun recalcularRutaSiEsNecesario() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRouteCalculation < MIN_RECALCULATION_INTERVAL) {
            return // Evitar spam de recálculos
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    estaCalculandoRuta = true,
                    navigationUiState = NavigationUiState.OffRoute(
                        it.ubicacionActual ?: return@update it,
                        true
                    )
                )
            }

            delay(500) // Pequeño delay para UI

            val casa = _uiState.value.casa ?: return@launch
            calcularRuta(casa, "foot-walking", isRecalculation = true)
        }
    }

    private fun calcularRuta(
        destino: LugarFavorito, 
        modo: String,
        isRecalculation: Boolean = false
    ) {
        routeCalculationJob?.cancel()

        routeCalculationJob = viewModelScope.launch {
            routeMutex.withLock {
                _uiState.update { 
                    it.copy(
                        estaCalculandoRuta = true,
                        uiState = UiState.Loading,
                        pantallaActual = Pantalla.NAVEGACION
                    ) 
                }

                try {
                    appModule.calcularRutaUseCase(destino, modo).fold(
                        onSuccess = { ruta ->
                            lastRouteCalculation = System.currentTimeMillis()
                            
                            val ubicacion = _uiState.value.ubicacionActual
                            val navigationState = if (ubicacion != null) {
                                NavigationState(
                                    userLocation = ubicacion,
                                    route = ruta,
                                    destination = destino,
                                    remainingDistance = ruta.distanciaMetros,
                                    remainingDuration = ruta.duracionSegundos,
                                    isOffRoute = false,
                                    distanceToRoute = 0.0,
                                    isFollowingUser = true,
                                    lastRecalculation = System.currentTimeMillis()
                                )
                            } else {
                                NavigationState(route = ruta, destination = destino)
                            }

                            _uiState.update {
                                it.copy(
                                    rutaActual = ruta,
                                    navigationState = navigationState,
                                    estaCalculandoRuta = false,
                                    uiState = UiState.Success(ruta),
                                    navigationUiState = NavigationUiState.Navigating(navigationState)
                                )
                            }
                        },
                        onFailure = { error ->
                            val errorType = when {
                                error.message?.contains("Unable to resolve host") == true ->
                                    ErrorType.NO_INTERNET
                                error.message?.contains("timeout") == true ->
                                    ErrorType.TIMEOUT
                                else -> ErrorType.API_ERROR
                            }

                            _uiState.update {
                                it.copy(
                                    error = error.message ?: "Error al calcular ruta",
                                    estaCalculandoRuta = false,
                                    uiState = UiState.Error(error.message ?: "Error"),
                                    navigationUiState = NavigationUiState.Error(
                                        error.message ?: "Error",
                                        errorType
                                    )
                                )
                            }
                        }
                    )
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            error = "Error de conexión",
                            estaCalculandoRuta = false,
                            uiState = UiState.Error("Sin internet", false),
                            navigationUiState = NavigationUiState.Error(
                                "Sin conexión",
                                ErrorType.NO_INTERNET
                            )
                        )
                    }
                }
            }
        }
    }

    fun detenerNavegacion() {
        locationTrackingJob?.cancel()
        routeCalculationJob?.cancel()
        appModule.locationTrackingService.stopLocationUpdates()
        
        _uiState.update {
            it.copy(
                isTrackingLocation = false,
                rutaActual = null,
                navigationState = NavigationState(),
                navigationUiState = NavigationUiState.Idle,
                uiState = UiState.Idle,
                pantallaActual = Pantalla.MAP
            )
        }
    }

    fun toggleFollowUser() {
        _uiState.update {
            it.copy(
                navigationState = it.navigationState.copy(
                    isFollowingUser = !it.navigationState.isFollowingUser
                )
            )
        }
    }

    fun guardarCasaDesdeLugar(lugar: Lugar) {
        val favorito = LugarFavorito(
            id = UUID.randomUUID().toString(),
            nombre = "Casa",
            direccion = lugar.direccion,
            latitud = lugar.latitud,
            longitud = lugar.longitud,
            tipo = LugarFavorito.TipoFavorito.CASA
        )

        viewModelScope.launch {
            _uiState.update { it.copy(estaGuardando = true, uiState = UiState.Loading) }
            
            try {
                appModule.guardarFavoritoUseCase(favorito).fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                casa = favorito,
                                estaGuardando = false,
                                busqueda = "",
                                resultadosBusqueda = emptyList(),
                                lugarSeleccionado = null,
                                uiState = UiState.Success(),
                                pantallaActual = Pantalla.MAP
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                error = error.message ?: "Error al guardar",
                                estaGuardando = false,
                                uiState = UiState.Error(error.message ?: "Error")
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Error al guardar casa",
                        estaGuardando = false,
                        uiState = UiState.Error("Error de base de datos")
                    )
                }
            }
        }
    }

    fun seleccionarLugar(lugar: Lugar) {
        _uiState.update {
            it.copy(
                lugarSeleccionado = lugar,
                busqueda = lugar.direccion,
                resultadosBusqueda = emptyList()
            )
        }
    }

    fun cambiarPantalla(pantalla: Pantalla) {
        _uiState.update { it.copy(pantallaActual = pantalla) }
    }

    fun limpiarError() {
        _uiState.update { it.copy(error = null, uiState = UiState.Idle) }
    }

    override fun onCleared() {
        super.onCleared()
        detenerNavegacion()
    }

    // ==================== MATH UTILS ====================

    private fun calcularDistanciaARuta(ubicacion: UbicacionUsuario, ruta: Ruta): Double {
        if (ruta.puntos.isEmpty()) return Double.MAX_VALUE

        var minDistance = Double.MAX_VALUE
        
        for (punto in ruta.puntos) {
            val dist = haversineDistance(
                ubicacion.latitud, ubicacion.longitud,
                punto.latitud, punto.longitud
            )
            if (dist < minDistance) {
                minDistance = dist
            }
        }

        return minDistance
    }

    private fun calcularDistanciaRestante(ubicacion: UbicacionUsuario, ruta: Ruta): Double {
        if (ruta.puntos.isEmpty()) return ruta.distanciaMetros

        // Encontrar el punto más cercano en la ruta
        var minIndex = 0
        var minDistance = Double.MAX_VALUE

        ruta.puntos.forEachIndexed { index, punto ->
            val dist = haversineDistance(
                ubicacion.latitud, ubicacion.longitud,
                punto.latitud, punto.longitud
            )
            if (dist < minDistance) {
                minDistance = dist
                minIndex = index
            }
        }

        // Calcular distancia restante desde ese punto
        var remainingDistance = 0.0
        for (i in minIndex until ruta.puntos.size - 1) {
            remainingDistance += haversineDistance(
                ruta.puntos[i].latitud, ruta.puntos[i].longitud,
                ruta.puntos[i + 1].latitud, ruta.puntos[i + 1].longitud
            )
        }

        return remainingDistance
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000 // Radio de la Tierra en metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    class Factory(private val appModule: AppModule) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NavigationViewModel::class.java)) {
                return NavigationViewModel(appModule) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
