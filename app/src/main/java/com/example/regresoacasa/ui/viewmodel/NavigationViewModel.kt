package com.example.regresoacasa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.regresoacasa.di.AppModule
import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.NavigationState
import com.example.regresoacasa.domain.model.NavigationStatus
import com.example.regresoacasa.domain.model.PuntoRuta
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.usecase.BuscarLugaresUseCase
import com.example.regresoacasa.data.location.LocationFilter
import com.example.regresoacasa.domain.utils.SnapToRoute
import com.example.regresoacasa.domain.usecase.GuardarFavoritoUseCase
import com.example.regresoacasa.ui.state.ErrorType
import com.example.regresoacasa.ui.state.MainUiState
import com.example.regresoacasa.ui.state.NavigationUiState
import com.example.regresoacasa.ui.state.Pantalla
import com.example.regresoacasa.ui.state.UiState
import com.example.regresoacasa.ui.state.ConnectionState
import com.example.regresoacasa.ui.state.SystemFeedbackState
import com.example.regresoacasa.domain.utils.SmoothLocationTransition
import com.example.regresoacasa.data.location.BatteryLevelListener
import com.example.regresoacasa.data.location.BatteryMode
import com.example.regresoacasa.data.safety.SafeReturnPreferences
import com.example.regresoacasa.data.safety.SafeReturnSession
import com.example.regresoacasa.utils.SafeHaptics
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    private var searchJob: Job? = null
    private var locationTrackingJob: Job? = null
    private var routeCalculationJob: Job? = null
    private var batteryMonitoringJob: Job? = null
    private val routeMutex = Mutex()
    
    // Filtros de precisión GPS y transiciones suaves (Hardening)
    private val locationFilter = LocationFilter(windowSize = 5)
    private val smoothLocationTransition = SmoothLocationTransition(viewModelScope, durationMillis = 300)
    private val ubicacionesRecientes = ArrayDeque<UbicacionUsuario>(10)
    private var tiempoFueraDeRuta: Long = 0
    private var ultimaVezEnRuta: Long = System.currentTimeMillis()
    
    // FASE 1: Tracking para reset de prioridad al volver a ruta
    private var wasOffRoute: Boolean = false
    
    // Feedback háptico robusto con manejo de errores
    private lateinit var safeHaptics: SafeHaptics
    val isHapticsAvailable: Boolean
        get() = if (::safeHaptics.isInitialized) safeHaptics.isAvailable else false
    
    // Tracking de recálculo
    private var lastRouteCalculation: Long = 0
    
    // Estados de conexión y batería (Hardening)
    private lateinit var batteryLevelListener: BatteryLevelListener
    private lateinit var safeReturnPreferences: SafeReturnPreferences
    private var batteryMonitoringJob: Job? = null
    private var currentBatteryMode: BatteryMode = BatteryMode.Normal
    private val LOW_BATTERY_INTERVAL = 10000L // 10s en modo ahorro
    private val NORMAL_INTERVAL = 3000L      // 3s en modo normal
    
    private val MIN_RECALCULATION_INTERVAL = 10000L // 10 segundos mínimo entre recálculos

    init {
        cargarCasa()
        setupSearchDebounce()
        // Hardening: Restaurar sesión de Regreso Seguro si existe
        viewModelScope.launch {
            restoreSafeReturnSession()
        }
    }
    
    /**
     * Inicializa componentes que requieren Context (llamar desde Factory o Activity)
     */
    fun initializeWithContext(context: Context) {
        batteryLevelListener = BatteryLevelListener(context)
        safeReturnPreferences = SafeReturnPreferences(context)
        safeHaptics = SafeHaptics(context)
        setupBatteryMonitoring()
    }
    
    /**
     * Monitoreo de nivel de batería para modo ahorro automático
     */
    private fun setupBatteryMonitoring() {
        if (!::batteryLevelListener.isInitialized) return
        
        batteryMonitoringJob?.cancel()
        batteryMonitoringJob = viewModelScope.launch {
            batteryLevelListener.batteryLevelFlow.collect { level ->
                val wasLowBattery = currentBatteryMode is BatteryMode.LowBattery
                
                when {
                    batteryLevelListener.shouldActivateLowBatteryMode(level) -> {
                        currentBatteryMode = BatteryMode.LowBattery
                        if (!wasLowBattery) {
                            // Entró en modo ahorro - actualizar UI
                            _uiState.update {
                                it.copy(
                                    batteryLevel = level,
                                    isLowBatteryMode = true,
                                    connectionState = ConnectionState.LowBattery(level)
                                )
                            }
                            // Reiniciar tracking con intervalos más largos
                            if (_uiState.value.isTrackingLocation) {
                                reiniciarTrackingConNuevosIntervalos()
                            }
                        }
                    }
                    batteryLevelListener.shouldResumeNormalMode(level) && wasLowBattery -> {
                        currentBatteryMode = BatteryMode.Normal
                        _uiState.update {
                            it.copy(
                                batteryLevel = level,
                                isLowBatteryMode = false,
                                connectionState = ConnectionState.Connected
                            )
                        }
                        // Volver a intervalos normales
                        if (_uiState.value.isTrackingLocation) {
                            reiniciarTrackingConNuevosIntervalos()
                        }
                    }
                    else -> {
                        // Solo actualizar nivel
                        _uiState.update { it.copy(batteryLevel = level) }
                    }
                }
            }
        }
    }
    
    /**
     * Restaurar sesión de Regreso Seguro si es válida
     */
    private suspend fun restoreSafeReturnSession() {
        if (!::safeReturnPreferences.isInitialized) return
        
        safeReturnPreferences.sessionFlow.collect { session ->
            if (session?.isValid() == true) {
                _uiState.update {
                    it.copy(
                        safeReturnSession = session,
                        isSafeReturnActive = true
                    )
                }
            }
            // Solo necesitamos el primer valor, cancelar colección
            // Nota: En un flow real esto se manejaría diferente
            return@collect
        }
    }
    
    /**
     * Detectar estado de conexión a internet
     */
    private fun checkInternetConnection(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
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
        // Cancelar búsqueda anterior si existe
        searchJob?.cancel()
        
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(estaBuscando = true, uiState = UiState.Loading) }
            
            try {
                appModule.buscarLugaresUseCase(query).collect { result ->
                    when (result) {
                        is com.example.regresoacasa.domain.model.ApiResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    resultadosBusqueda = result.data,
                                    estaBuscando = false,
                                    uiState = UiState.Success()
                                )
                            }
                        }
                        is com.example.regresoacasa.domain.model.ApiResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    error = result.error.message,
                                    estaBuscando = false,
                                    uiState = UiState.Error(result.error.message)
                                )
                            }
                        }
                    }
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

        // Feedback háptico: Navegación iniciada (con fallback visual)
        safeHaptics.navigationStarted()
        
        // FASE 1: UX REDUNDANCY - Si no hay vibración, mostrar banner en UI
        if (!isHapticsAvailable) {
            _uiState.update { 
                it.copy(
                    navigationState = it.navigationState.copy(
                        systemFeedback = SystemFeedbackState.HapticsUnavailable
                    )
                ) 
            }
        }
        
        // Reset de prioridad de vibraciones al iniciar navegación
        safeHaptics.resetPriority()

        // Iniciar tracking continuo
        iniciarTrackingContinuo()

        // Calcular ruta inicial
        calcularRuta(casa, modo)
    }

    private fun iniciarTrackingContinuo(intervalMillis: Long = NORMAL_INTERVAL) {
        // Cancelar tracking anterior si existe
        locationTrackingJob?.cancel()

        locationTrackingJob = viewModelScope.launch {
            _uiState.update { it.copy(isTrackingLocation = true) }
            
            try {
                appModule.locationTrackingService
                    .startLocationUpdates(intervalMillis = intervalMillis, fastestIntervalMillis = intervalMillis / 2)
                    .catch { e ->
                        _uiState.update {
                            it.copy(
                                uiState = UiState.Error("Error de ubicación: ${e.message}"),
                                isTrackingLocation = false,
                                hasGpsSignal = false,
                                connectionState = ConnectionState.GpsLost
                            )
                        }
                    }
                    .collect { ubicacion ->
                        // Actualizar precisión GPS en estado
                        _uiState.update {
                            it.copy(
                                gpsAccuracy = ubicacion.precision,
                                hasGpsSignal = ubicacion.precision < 50f // Señal válida si < 50m
                            )
                        }
                        actualizarUbicacionEnNavegacion(ubicacion)
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        uiState = UiState.Error("Permiso de ubicación requerido", false),
                        isTrackingLocation = false,
                        hasGpsSignal = false,
                        connectionState = ConnectionState.GpsLost
                    )
                }
            }
        }
    }
    
    /**
     * Reinicia el tracking con nuevos intervalos (para modo ahorro)
     */
    private fun reiniciarTrackingConNuevosIntervalos() {
        val interval = if (currentBatteryMode is BatteryMode.LowBattery) {
            LOW_BATTERY_INTERVAL
        } else {
            NORMAL_INTERVAL
        }
        iniciarTrackingContinuo(interval)
    }

    private fun actualizarUbicacionEnNavegacion(ubicacionRaw: UbicacionUsuario) {
        val currentState = _uiState.value
        val ruta = currentState.rutaActual ?: return

        // ============ PIPELINE DE PROCESAMIENTO GPS (HARDENING) ============
        // ORDEN CRÍTICO: raw → filter → smooth → snap → state
        
        // 1. FILTRO: Suavizar ubicación GPS (moving average)
        val ubicacionFiltrada = locationFilter.filter(ubicacionRaw)
        
        // 2. SMOOTH: Transición suave para evitar saltos bruscos
        val (smoothedLat, smoothedLon) = smoothLocationTransition.getSmoothedLocation(
            ubicacionFiltrada.latitud, 
            ubicacionFiltrada.longitud
        )
        val ubicacionSuavizada = ubicacionFiltrada.copy(
            latitud = smoothedLat,
            longitud = smoothedLon
        )
        
        // 3. SNAP: Ajustar a la ruta más cercana
        val ubicacion = SnapToRoute.snap(ubicacionSuavizada, ruta, maxDistance = 25.0)

        // 4. TRACKING DE HISTORIAL para dirección de movimiento
        ubicacionesRecientes.addLast(ubicacion)
        if (ubicacionesRecientes.size > 10) {
            ubicacionesRecientes.removeFirst()
        }

        // 5. Calcular distancia a la ruta
        val distanciaARuta = calcularDistanciaARuta(ubicacion, ruta)
        val isOffRoute = distanciaARuta > 50 // 50 metros de tolerancia

        // 6. Tracking de tiempo fuera de ruta (para re-routing inteligente)
        val currentTime = System.currentTimeMillis()
        if (isOffRoute) {
            tiempoFueraDeRuta += (currentTime - ultimaVezEnRuta)
        } else {
            tiempoFueraDeRuta = 0
            ultimaVezEnRuta = currentTime
        }

        // 7. Calcular distancia restante y tiempo
        val remainingDistance = calcularDistanciaRestante(ubicacion, ruta)
        val remainingDuration = (remainingDistance / ruta.distanciaMetros) * ruta.duracionSegundos

        // 8. DETECCIÓN DE LLEGADA (nueva)
        val hasArrived = detectarLlegada(remainingDistance, ubicacion)
        
        // 9. TURN-BY-TURN NAVIGATION (nueva)
        val (currentInstruction, distanceToNextTurn, progressToNextTurn) = calcularInstruccionActual(
            ubicacion, ruta, remainingDistance
        )
        
        // FASE 2: Anti-flicker - calcular bucket estable (nunca sube)
        val newBucket = getDistanceBucket(distanceToNextTurn)
        val stableBucket = if (newBucket < currentState.navigationState.distanceBucketStable) {
            newBucket
        } else {
            currentState.navigationState.distanceBucketStable
        }
        
        // FASE 3: Suavizado visual de distancia (lerp 0.2)
        val displayedDistance = lerp(
            currentState.navigationState.displayedDistance,
            distanceToNextTurn,
            0.2
        )
        
        // FASE 4: Snap-to-route honesto - calcular desviación real
        val realDeviation = if (ubicacion != currentState.navigationState.userLocation) {
            haversineDistance(
                ubicacion.latitud, ubicacion.longitud,
                currentState.navigationState.userLocation?.latitud ?: ubicacion.latitud,
                currentState.navigationState.userLocation?.longitud ?: ubicacion.longitud
            )
        } else 0.0
        
        // FASE 5: Estado global claro
        val navigationStatus = when {
            hasArrived -> NavigationStatus.ARRIVING
            currentState.estaCalculandoRuta -> NavigationStatus.RECALCULATING
            isOffRoute -> NavigationStatus.OFF_ROUTE
            uiState.gpsAccuracy != null && uiState.gpsAccuracy > 25f -> NavigationStatus.GPS_WEAK
            else -> NavigationStatus.NORMAL
        }
        
        // 10. Vibraciones de proximidad (nueva)
        if (!hasArrived && currentInstruction != null) {
            // Usar distancia estable para haptics
            val stableDistance = when (stableBucket) {
                20 -> 20.0
                50 -> 50.0
                100 -> 100.0
                else -> distanceToNextTurn
            }
            procesarVibracionesProximidad(stableDistance, isOffRoute)
        }
        
        // FASE 1: Reset de prioridad cuando vuelve a ruta después de desviarse
        if (wasOffRoute && !isOffRoute) {
            safeHaptics.resetPriority()
        }
        wasOffRoute = isOffRoute

        // 11. Actualizar estado de navegación
        val navigationState = NavigationState(
            userLocation = ubicacion,
            route = ruta,
            destination = currentState.casa,
            remainingDistance = remainingDistance,
            remainingDuration = remainingDuration,
            isOffRoute = isOffRoute,
            distanceToRoute = distanciaARuta,
            isFollowingUser = currentState.navigationState.isFollowingUser,
            lastRecalculation = currentState.navigationState.lastRecalculation,
            // Campos nuevos
            currentInstruction = currentInstruction,
            distanceToNextTurn = distanceToNextTurn,
            progressToNextTurn = progressToNextTurn,
            hasArrived = hasArrived || currentState.navigationState.hasArrived,
            totalDistance = ruta.distanciaMetros,
            elapsedTime = if (currentState.navigationState.startTime > 0) {
                currentTime - currentState.navigationState.startTime
            } else 0,
            startTime = if (currentState.navigationState.startTime > 0) {
                currentState.navigationState.startTime
            } else currentTime,
            // Preservar estado de feedback háptico
            systemFeedback = currentState.navigationState.systemFeedback,
            // FASE 1-5: Estados de consistencia
            navigationStatus = navigationStatus,
            distanceToNextTurnStable = when (stableBucket) {
                20 -> 20.0
                50 -> 50.0
                100 -> 100.0
                else -> distanceToNextTurn
            },
            distanceBucketStable = stableBucket,
            displayedDistance = displayedDistance,
            realDeviation = realDeviation
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

        // 9. Re-routing automático inteligente (>100m Y >5s fuera de ruta)
        if (isOffRoute && distanciaARuta > 100 && tiempoFueraDeRuta > 5000) {
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

                // Obtener ubicación actual para pasar al UseCase
                val ubicacion = _uiState.value.ubicacionActual
                if (ubicacion == null) {
                    _uiState.update {
                        it.copy(
                            error = "Ubicación no disponible",
                            estaCalculandoRuta = false,
                            uiState = UiState.Error("GPS no disponible", false),
                            navigationUiState = NavigationUiState.Error(
                                "Ubicación requerida",
                                ErrorType.GPS_ERROR
                            )
                        )
                    }
                    return@withLock
                }

                try {
                    when (val result = appModule.calcularRutaUseCase(ubicacion, destino, modo)) {
                        is com.example.regresoacasa.domain.model.ApiResult.Success -> {
                            val ruta = result.data
                            lastRouteCalculation = System.currentTimeMillis()
                            
                            val navigationState = NavigationState(
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

                            _uiState.update {
                                it.copy(
                                    rutaActual = ruta,
                                    navigationState = navigationState,
                                    estaCalculandoRuta = false,
                                    uiState = UiState.Success(ruta),
                                    navigationUiState = NavigationUiState.Navigating(navigationState)
                                )
                            }
                            
                            // FASE 5: Resetear prioridad después de recalcular ruta
                            safeHaptics.resetPriority()
                            safeHaptics.routeRecalculated()
                        }
                        is com.example.regresoacasa.domain.model.ApiResult.Error -> {
                            val errorType = when (result.error) {
                                is com.example.regresoacasa.domain.model.ApiError.InvalidApiKey -> ErrorType.API_KEY_INVALID
                                is com.example.regresoacasa.domain.model.ApiError.NoInternet -> ErrorType.NO_INTERNET
                                is com.example.regresoacasa.domain.model.ApiError.Timeout -> ErrorType.TIMEOUT
                                is com.example.regresoacasa.domain.model.ApiError.ServerError -> ErrorType.SERVER_ERROR
                                is com.example.regresoacasa.domain.model.ApiError.NotFound -> ErrorType.NO_ROUTE
                                else -> ErrorType.API_ERROR
                            }

                            _uiState.update {
                                it.copy(
                                    error = result.error.message,
                                    estaCalculandoRuta = false,
                                    uiState = UiState.Error(result.error.message),
                                    navigationUiState = NavigationUiState.Error(
                                        result.error.message,
                                        errorType
                                    )
                                )
                            }
                        }
                    }
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
        // Cancelar todos los jobs activos para evitar memory leaks
        searchJob?.cancel()
        locationTrackingJob?.cancel()
        routeCalculationJob?.cancel()
        batteryMonitoringJob?.cancel()
        
        // Detener servicios de ubicación
        appModule.locationTrackingService.stopLocationUpdates()
        
        // Limpiar buffers
        ubicacionesRecientes.clear()
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

    // FASE 3: Suavizado visual
    private fun lerp(start: Double, end: Double, fraction: Double): Double {
        return start + (end - start) * fraction
    }

    // ==================== TURN-BY-TURN & ARRIVAL LOGIC ====================

    private var lastVibrationDistance: Double = Double.MAX_VALUE
    private var hasArrivedTriggered: Boolean = false
    
    // RIESGO 2: Distance Lock - evita vibraciones repetidas en el mismo bucket
    private var lastTriggeredDistanceBucket: Int? = null
    
    /**
     * RIESGO 2: Obtiene el bucket de distancia para evitar eventos repetidos
     */
    private fun getDistanceBucket(distance: Double): Int {
        return when {
            distance <= 20 -> 20
            distance <= 50 -> 50
            distance <= 100 -> 100
            else -> 999
        }
    }

    /**
     * Detecta si el usuario ha llegado al destino
     * Criterios: < 30m del destino y sin movimiento significativo
     */
    private fun detectarLlegada(remainingDistance: Double, ubicacion: UbicacionUsuario): Boolean {
        if (hasArrivedTriggered) return true
        
        // Si estamos a menos de 30m del destino
        if (remainingDistance < 30) {
            // Verificar si hay historial suficiente
            if (ubicacionesRecientes.size >= 3) {
                // Calcular movimiento reciente
                val recentLocations = ubicacionesRecientes.takeLast(3)
                val totalMovement = recentLocations.zipWithNext { a, b ->
                    haversineDistance(a.latitud, a.longitud, b.latitud, b.longitud)
                }.sum()
                
                // Si se movió menos de 5m en las últimas 3 actualizaciones = llegada
                if (totalMovement < 5.0) {
                    hasArrivedTriggered = true
                    // Vibración de celebración (con manejo de errores automático)
                    safeHaptics.arrivalCelebration()
                    // FASE 5: Resetear prioridad después de llegada
                    safeHaptics.resetPriority()
                    return true
                }
            }
        }
        return false
    }

    /**
     * Calcula la instrucción actual de navegación turn-by-turn
     * MVP: Simplificado - solo "Continúa recto" basado en la ruta
     */
    private fun calcularInstruccionActual(
        ubicacion: UbicacionUsuario,
        ruta: Ruta,
        remainingDistance: Double
    ): Triple<InstruccionNavegacion?, Double, Float> {
        // MVP simplificado: Usar distancia restante como proxy de instrucción
        // En una implementación completa, analizaríamos los puntos de la ruta
        // para detectar giros reales
        
        val instruction = if (remainingDistance < 50) {
            InstruccionNavegacion(
                texto = "Estás llegando",
                distancia = remainingDistance,
                tipo = TipoManiobra.DESTINO
            )
        } else {
            InstruccionNavegacion(
                texto = "Continúa hacia tu destino",
                distancia = remainingDistance,
                tipo = TipoManiobra.CONTINUA_RECTO
            )
        }
        
        // Calcular progreso hacia la siguiente "maniobra" (simplificado)
        val totalDistance = ruta.distanciaMetros
        val progress = if (totalDistance > 0) {
            ((totalDistance - remainingDistance) / totalDistance).toFloat()
        } else 0f
        
        return Triple(instruction, remainingDistance, progress.coerceIn(0f, 1f))
    }

    /**
     * RIESGO 2: Procesa las vibraciones de proximidad para giros con Distance Lock
     * Evita vibraciones repetidas cuando el GPS fluctúa
     */
    private fun procesarVibracionesProximidad(distanceToNextTurn: Double, isOffRoute: Boolean) {
        // RIESGO 4: Silencio inteligente - no vibrar si está quieto
        if (ubicacionesRecientes.size >= 2) {
            val recentLocations = ubicacionesRecientes.takeLast(2)
            val movement = haversineDistance(
                recentLocations[0].latitud, recentLocations[0].longitud,
                recentLocations[1].latitud, recentLocations[1].longitud
            )
            if (movement < 0.5) {
                // Usuario quieto - no vibrar
                return
            }
        }
        
        // UX REDUNDANCY: Vibración + Visual intensificado si no hay haptics
        if (isOffRoute && lastVibrationDistance > 100) {
            safeHaptics.offRouteDetected()
            lastVibrationDistance = 0.0
            lastTriggeredDistanceBucket = null
            return
        }
        
        // RIESGO 2: Distance Lock - solo vibrar si cambia el bucket
        val currentBucket = getDistanceBucket(distanceToNextTurn)
        
        if (currentBucket != lastTriggeredDistanceBucket) {
            lastTriggeredDistanceBucket = currentBucket
            
            when (currentBucket) {
                20 -> safeHaptics.turnApproaching20m()
                50 -> safeHaptics.turnApproaching50m()
                100 -> safeHaptics.turnApproaching100m()
            }
        }
        
        lastVibrationDistance = distanceToNextTurn
    }

    // ==================== FUNCIONES DE LLEGADA ====================

    /**
     * Compartir llegada exitosa a contacto de confianza
     */
    fun shareArrival() {
        // Vibración de celebración (con manejo de errores automático en SafeHaptics)
        safeHaptics.arrivalCelebration()
        
        val session = _uiState.value.safeReturnSession
        if (session != null) {
            // Aquí se implementaría el envío real al contacto
            // Por ahora, solo marcamos como completado
            viewModelScope.launch {
                safeReturnPreferences.clearSession()
            }
        }
        
        dismissArrival()
    }

    /**
     * Cerrar pantalla de celebración de llegada
     */
    fun dismissArrival() {
        _uiState.update {
            it.copy(
                navigationState = it.navigationState.copy(hasArrived = false)
            )
        }
        detenerNavegacion()
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
