package com.example.regresoacasa.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.regresoacasa.di.AppModule
import com.example.regresoacasa.domain.model.*
import com.example.regresoacasa.data.location.LocationFilter
import com.example.regresoacasa.data.location.LocationForegroundService
import com.example.regresoacasa.domain.utils.SnapToRoute
import com.example.regresoacasa.ui.state.*
import com.example.regresoacasa.domain.utils.SmoothLocationTransition
import com.example.regresoacasa.data.location.BatteryLevelListener
import com.example.regresoacasa.data.location.BatteryMode
import com.example.regresoacasa.data.safety.SafeReturnPreferences
import com.example.regresoacasa.data.local.PreferencesManager
import com.example.regresoacasa.data.safety.ReliableAlertDispatcher
import com.example.regresoacasa.data.safety.SafetyWatchdog
import com.example.regresoacasa.data.safety.BatteryOptimizationHelper
import com.example.regresoacasa.data.safety.RiskEvaluator
import com.example.regresoacasa.data.safety.SuspiciousSilenceDetector
import com.example.regresoacasa.data.safety.AlertMessageFormatter
import com.example.regresoacasa.data.safety.AdaptiveAlertThresholds
import com.example.regresoacasa.data.location.SafetyForegroundService
import com.example.regresoacasa.utils.SafeHaptics
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.utils.AnalyticsLogger
import com.example.regresoacasa.domain.utils.FallbackRoute
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.*
import kotlin.collections.ArrayDeque

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
    private lateinit var guardianManager: com.example.regresoacasa.data.safety.GuardianManager
    val isHapticsAvailable: Boolean
        get() = if (::safeHaptics.isInitialized) safeHaptics.isAvailable else false
    
    // Tracking de recálculo
    private var lastRouteCalculation: Long = 0
    
    // Estados de conexión y batería (Hardening)
    private lateinit var batteryLevelListener: BatteryLevelListener
    private lateinit var safeReturnPreferences: SafeReturnPreferences
    private lateinit var preferencesManager: PreferencesManager
    private var batteryMonitoringJob: Job? = null
    private var currentBatteryMode: BatteryMode = BatteryMode.Normal
    private val LOW_BATTERY_INTERVAL = 10000L // 10s en modo ahorro
    private val NORMAL_INTERVAL = 3000L      // 3s en modo normal
    
    private val MIN_RECALCULATION_INTERVAL = 10000L // 10 segundos mínimo entre recálculos

    // TTS Manager para instrucciones por voz
    private var ttsManager: com.example.regresoacasa.utils.TtsManager? = null
    private var lastInstructionSpoken: String? = null

    // Safety Trip Components
    private var currentSafeTripId: String? = null
    private var emergencyContacts: List<com.example.regresoacasa.data.safety.EmergencyContact> = emptyList()
    
    // FASE 4: SafeClickHandler para prevenir spam
    private val safeClickHandler = com.example.regresoacasa.ui.components.SafeClickHandler()
    
    // New Safety System Components
    private lateinit var reliableAlertDispatcher: ReliableAlertDispatcher
    private lateinit var safetyWatchdog: SafetyWatchdog
    private lateinit var batteryOptimizationHelper: BatteryOptimizationHelper
    private lateinit var riskEvaluator: RiskEvaluator
    private lateinit var suspiciousSilenceDetector: SuspiciousSilenceDetector
    private lateinit var alertMessageFormatter: AlertMessageFormatter
    private lateinit var adaptiveAlertThresholds: AdaptiveAlertThresholds

    init {
        cargarCasa()
        setupSearchDebounce()
        // Hardening: Restaurar sesión de Regreso Seguro si existe
        viewModelScope.launch {
            restoreSafeReturnSession()
        }
        // FASE: Obtener ubicación inicial inmediatamente para centrar mapa en México y no en el mar
        obtenerUbicacionUnica()
    }
    
    /**
     * Inicializa componentes que requieren Context (llamar desde Factory o Activity)
     */
    fun initializeWithContext(context: Context) {
        batteryLevelListener = BatteryLevelListener(context)
        safeReturnPreferences = SafeReturnPreferences(context)
        preferencesManager = PreferencesManager(context)
        safeHaptics = SafeHaptics(context)
        guardianManager = com.example.regresoacasa.data.safety.GuardianManager(context, safeHaptics)
        ttsManager = com.example.regresoacasa.utils.TtsManager(context)
        
        // Initialize new safety components
        batteryOptimizationHelper = BatteryOptimizationHelper(context)
        reliableAlertDispatcher = ReliableAlertDispatcher(
            context,
            (context.applicationContext as com.example.regresoacasa.RegresoACasaApp).database.alertDeliveryDao(),
            viewModelScope
        )
        safetyWatchdog = SafetyWatchdog(
            context,
            preferencesManager,
            viewModelScope
        )
        riskEvaluator = RiskEvaluator()
        suspiciousSilenceDetector = SuspiciousSilenceDetector(viewModelScope)
        alertMessageFormatter = AlertMessageFormatter(context)
        adaptiveAlertThresholds = AdaptiveAlertThresholds()
        
        // Register SMS receivers
        reliableAlertDispatcher.registerReceivers()
        
        setupBatteryMonitoring()

        // Observar estado del Guardian
        viewModelScope.launch {
            guardianManager.isGuardianActive.collect { active ->
                _uiState.update { it.copy(isSafeReturnActive = active) }
            }
        }
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
     * CRÍTICO: Usar first() para evitar race condition en collect
     */
    private suspend fun restoreSafeReturnSession() {
        if (!::safeReturnPreferences.isInitialized) return
        
        try {
            val session = safeReturnPreferences.sessionFlow.first()
            if (session?.isValid() == true) {
                _uiState.update {
                    it.copy(
                        safeReturnSession = session,
                        isSafeReturnActive = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("NavigationViewModel", "Error restoring safe return session", e)
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
            .debounce(1000)
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
                                estaCargandoUbicacion = false,
                                uiState = UiState.Error(error.message ?: "Error GPS")
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
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
                    uiState = UiState.Error("Configura tu casa primero", false)
                ) 
            }
            return
        }
        
        val lugarCasa = Lugar(
            id = casa.id,
            nombre = casa.nombre,
            direccion = casa.direccion,
            latitud = casa.latitud,
            longitud = casa.longitud
        )
        iniciarNavegacionConDestino(lugarCasa, modo)
    }

    fun iniciarNavegacionConDestino(destino: Lugar, modo: String = "foot-walking") {
        // FASE 2: Validación de API Key antes de iniciar navegación
        if (!(appModule.appContext as com.example.regresoacasa.RegresoACasaApp).isApiKeyValid) {
            _uiState.update { 
                it.copy(
                    uiState = UiState.Error("API Key inválida. Configúrala en local.properties", false)
                ) 
            }
            AnalyticsLogger.logApiKeyInvalid()
            return
        }
        
        // FASE 4: SafeClick - Prevenir spam
        if (!safeClickHandler.safeClick {
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
            lastInstructionSpoken = null

            // Start safety foreground service
            SafetyForegroundService.startService(appModule.appContext)
            
            // Start watchdog
            safetyWatchdog.start()
            
            // Start silence detector
            suspiciousSilenceDetector.startMonitoring()
            
            // Check battery optimization
            if (::batteryOptimizationHelper.isInitialized && batteryOptimizationHelper.shouldShowBatteryOptimizationWarning()) {
                // TODO: Show battery optimization warning in UI
                Log.w("NavigationViewModel", "Battery optimization warning should be shown")
            }

            // Iniciar tracking continuo
            iniciarTrackingContinuo()

            val lugarDestino = destino

            _uiState.update { 
                it.copy(
                    pantallaActual = Pantalla.NAVEGACION,
                    navigationState = it.navigationState.copy(
                        startTime = System.currentTimeMillis(),
                        hasArrived = false,
                        isFollowingUser = true,
                        destination = lugarDestino
                    )
                ) 
            }

            // Calcular ruta inicial
            calcularRuta(lugarDestino, modo)
            
            // FASE 10: Log evento
            AnalyticsLogger.logNavigationStarted(destino.nombre)
        }) {
            // Click bloqueado por cooldown
            Log.d("NavigationViewModel", "Click bloqueado por cooldown")
        }
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
                                hasGpsSignal = (ubicacion.precision ?: 100f) < 50f // Señal válida si < 50m
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
        
        // CRÍTICO: Iniciar ForegroundService para Android 10+ background tracking
        try {
            LocationForegroundService.start(appModule.appContext, intervalMillis)
        } catch (e: Exception) {
            Log.e("NavigationViewModel", "Error starting foreground service", e)
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
        // Update watchdog timestamp
        if (::safetyWatchdog.isInitialized) {
            safetyWatchdog.updateGpsTimestamp(System.currentTimeMillis())
            safetyWatchdog.updateMonitorCycleTimestamp(System.currentTimeMillis())
        }
        
        // Actualizar Guardian
        if (::guardianManager.isInitialized) {
            guardianManager.updateLocation(ubicacionRaw)
        }

        val currentState = _uiState.value
        val ruta = currentState.rutaActual ?: return

        // FASE 6: Threading real - mover cálculos GPS fuera del main thread
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    procesarUbicacionEnNavegacion(ubicacionRaw, currentState, ruta)
                }
            } catch (e: Exception) {
                Log.e("NavigationViewModel", "Error procesando ubicación en navegación", e)
                _uiState.update {
                    it.copy(
                        uiState = UiState.Error("Error de GPS: ${e.message}")
                    )
                }
            }
        }
    }

    private suspend fun procesarUbicacionEnNavegacion(
        ubicacionRaw: UbicacionUsuario,
        currentState: MainUiState,
        ruta: Ruta
    ) {

        // ============ PIPELINE DE PROCESAMIENTO GPS (HARDENING) ============
        // ORDEN CRÍTICO: raw → filter → smooth → velocity → snap → state
        
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
        
        // 3. VELOCITY: Calcular velocidad actual para snap-to-route dinámico
        val velocidadMs = calcularVelocidadActual(ubicacionSuavizada)
        
        // 4. SNAP: Ajustar a la ruta más cercana con tolerancia dinámica según velocidad
        val maxDistanceDinamico = calcularMaxDistancePorVelocidad(velocidadMs)
        val ubicacion = SnapToRoute.snap(ubicacionSuavizada, ruta, maxDistance = maxDistanceDinamico)

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

        // 7. Calcular distancia restante y tiempo (ETA mejorado)
        val remainingDistance = calcularDistanciaRestante(ubicacion, ruta)
        
        // FASE 6: ETA Dinámico basado en velocidad real + teórica
        val theoreticalSpeed = ruta.distanciaMetros / ruta.duracionSegundos
        val currentSpeed = velocidadMs.coerceIn(0.5, 30.0) // Clamp entre 0.5m/s y 30m/s (coche)
        
        // Mezclamos: 30% velocidad actual, 70% teórica para evitar saltos bruscos
        val blendedSpeed = (currentSpeed * 0.3) + (theoreticalSpeed * 0.7)
        val remainingDuration = (remainingDistance / blendedSpeed).coerceAtLeast(0.0)
        
        // Calcular ETA (Hora estimada de llegada)
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.SECOND, remainingDuration.toInt())
        val etaFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val eta = etaFormat.format(calendar.time)

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
            (currentState.gpsAccuracy ?: 0f) > 25f -> NavigationStatus.GPS_WEAK
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

        // 9. Turn-by-turn Voice (TTS)
        currentInstruction?.let { inst ->
            if (inst.texto != lastInstructionSpoken) {
                // Solo hablar si la distancia es significativa o es un cambio de instrucción
                if (distanceToNextTurn < 100 || lastInstructionSpoken == null) {
                    ttsManager?.speak(inst.texto)
                    lastInstructionSpoken = inst.texto
                }
            }
        }

        // 11. Actualizar estado de navegación
        val navigationState = NavigationState(
            userLocation = ubicacion,
            route = ruta,
            destination = currentState.navigationState.destination,
            remainingDistance = remainingDistance,
            remainingDuration = remainingDuration,
            transportMode = currentState.navigationState.transportMode,
            eta = eta,
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
            realDeviation = realDeviation,
            currentSpeedKmh = (velocidadMs * 3.6).toInt()
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

            val destino = _uiState.value.navigationState.destination ?: return@launch
            calcularRuta(destino, "foot-walking", isRecalculation = true)
        }
    }

    private fun calcularRuta(
        destino: Lugar, 
        modo: String,
        origen: UbicacionUsuario? = null,
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

                // Obtener origen (parámetro o ubicación actual)
                val puntoOrigen = origen ?: _uiState.value.ubicacionActual
                
                if (puntoOrigen == null) {
                    _uiState.update {
                        it.copy(
                            estaCalculandoRuta = false,
                            uiState = UiState.Error("Origen no disponible", false),
                            navigationUiState = NavigationUiState.Error(
                                "Ubicación de origen requerida",
                                ErrorType.GPS_ERROR
                            )
                        )
                    }
                    return@withLock
                }

                try {
                    val puntoDestino = UbicacionUsuario(destino.latitud, destino.longitud)
                    when (val result = appModule.calcularRutaUseCase(puntoOrigen, puntoDestino, modo)) {
                        is com.example.regresoacasa.domain.model.ApiResult.Success -> {
                            val ruta = result.data
                            lastRouteCalculation = System.currentTimeMillis()
                            
                            val navigationState = com.example.regresoacasa.domain.model.NavigationState(
                                userLocation = puntoOrigen,
                                route = ruta,
                                destination = destino,
                                remainingDistance = ruta.distanciaMetros,
                                remainingDuration = ruta.duracionSegundos,
                                transportMode = modo,
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
                            // FASE 3: Fallback route cuando falla API
                            val errorType = when (result.error) {
                                is com.example.regresoacasa.domain.model.ApiError.InvalidApiKey -> ErrorType.API_KEY_INVALID
                                is com.example.regresoacasa.domain.model.ApiError.NoInternet -> ErrorType.NO_INTERNET
                                is com.example.regresoacasa.domain.model.ApiError.Timeout -> ErrorType.TIMEOUT
                                is com.example.regresoacasa.domain.model.ApiError.ServerError -> ErrorType.SERVER_ERROR
                                is com.example.regresoacasa.domain.model.ApiError.NotFound -> ErrorType.NO_ROUTE
                                else -> ErrorType.API_ERROR
                            }
                            
                            // FASE 3: Usar fallback route si es error de red/servidor
                            if (result.error is com.example.regresoacasa.domain.model.ApiError.NoInternet || 
                                result.error is com.example.regresoacasa.domain.model.ApiError.Timeout ||
                                result.error is com.example.regresoacasa.domain.model.ApiError.ServerError) {
                                
                                AnalyticsLogger.logRouteFailed(result.error.javaClass.simpleName)
                                
                                val fallbackRuta = FallbackRoute.fallbackRoute(
                                    puntoOrigen.latitud, puntoOrigen.longitud,
                                    puntoDestino.latitud, puntoDestino.longitud
                                )
                                
                                val navigationState = com.example.regresoacasa.domain.model.NavigationState(
                                    userLocation = puntoOrigen,
                                    route = fallbackRuta,
                                    destination = destino,
                                    remainingDistance = fallbackRuta.distanciaMetros,
                                    remainingDuration = fallbackRuta.duracionSegundos,
                                    transportMode = modo,
                                    isOffRoute = false,
                                    distanceToRoute = 0.0,
                                    isFollowingUser = true,
                                    lastRecalculation = System.currentTimeMillis()
                                )
                                
                                _uiState.update {
                                    it.copy(
                                        rutaActual = fallbackRuta,
                                        navigationState = navigationState,
                                        estaCalculandoRuta = false,
                                        uiState = UiState.Success(fallbackRuta),
                                        navigationUiState = NavigationUiState.Navigating(navigationState),
                                        connectionState = ConnectionState.NoInternet
                                    )
                                }
                                
                                AnalyticsLogger.logNoInternet()
                                return@withLock
                            }

                            _uiState.update {
                                it.copy(
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
        // Stop safety components
        if (::safetyWatchdog.isInitialized) {
            safetyWatchdog.stop()
        }
        if (::suspiciousSilenceDetector.isInitialized) {
            suspiciousSilenceDetector.stopMonitoring()
        }
        if (::reliableAlertDispatcher.isInitialized) {
            reliableAlertDispatcher.unregisterReceivers()
        }
        SafetyForegroundService.stopService(appModule.appContext)
        
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
                                estaGuardando = false,
                                uiState = UiState.Error(error.message ?: "Error")
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
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

    fun eliminarCasa() {
        val casaId = _uiState.value.casa?.id ?: return
        viewModelScope.launch {
            try {
                appModule.mapRepository.eliminarFavorito(casaId)
                _uiState.update { it.copy(casa = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(uiState = UiState.Error("Error al eliminar casa")) }
            }
        }
    }

    // ==================== SELECCIÓN EN MAPA ====================

    fun iniciarSeleccionMapa() {
        _uiState.update {
            it.copy(
                isSelectingOnMap = true,
                pantallaActual = Pantalla.MAP,
                // Centrar inicialmente en ubicación actual si existe
                mapCenterUbicacion = it.mapCenterUbicacion ?: it.ubicacionActual
            )
        }
    }

    fun onMapMove(lat: Double, lon: Double) {
        if (!_uiState.value.isSelectingOnMap) return
        
        _uiState.update {
            it.copy(
                mapCenterUbicacion = UbicacionUsuario(lat, lon)
            )
        }
    }

    fun onMapLongClick(lat: Double, lon: Double) {
        _uiState.update {
            it.copy(
                mapCenterUbicacion = UbicacionUsuario(lat, lon),
                // No cerramos el modo selección inmediatamente para que el usuario vea el marcador
                isSelectingOnMap = true
            )
        }
    }

    fun confirmarSeleccionMapa() {
        val center = _uiState.value.mapCenterUbicacion ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(estaBuscando = true, uiState = UiState.Loading) }
            
            try {
                when (val result = appModule.obtenerDireccionUseCase(center.latitud, center.longitud)) {
                    is com.example.regresoacasa.domain.model.ApiResult.Success -> {
                        val lugar = result.data
                        _uiState.update {
                            it.copy(
                                lugarSeleccionado = lugar,
                                busqueda = lugar.direccion,
                                isSelectingOnMap = false,
                                estaBuscando = false,
                                uiState = UiState.Success()
                            )
                        }
                    }
                    is com.example.regresoacasa.domain.model.ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                estaBuscando = false,
                                uiState = UiState.Error(result.error.message),
                                isSelectingOnMap = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        estaBuscando = false,
                        uiState = UiState.Error("Error al obtener dirección"),
                        isSelectingOnMap = false
                    )
                }
            }
        }
    }

    fun cancelarSeleccionMapa() {
        _uiState.update {
            it.copy(
                isSelectingOnMap = false,
                mapCenterUbicacion = null
            )
        }
    }

    fun cambiarEstiloMapa(nuevoEstilo: String) {
        _uiState.update { it.copy(mapStyle = nuevoEstilo) }
    }

    fun toggleGuardian(phoneNumber: String) {
        if (uiState.value.isSafeReturnActive) {
            guardianManager.deactivateGuardian()
        } else {
            guardianManager.activateGuardian(phoneNumber)
        }
    }

    fun sendEmergencyAlert() {
        guardianManager.sendEmergencyAlert()
    }

    fun limpiarError() {
        _uiState.update { it.copy(uiState = UiState.Idle) }
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup safety components
        if (::reliableAlertDispatcher.isInitialized) {
            reliableAlertDispatcher.unregisterReceivers()
        }
        if (::safetyWatchdog.isInitialized) {
            safetyWatchdog.stop()
        }
        if (::suspiciousSilenceDetector.isInitialized) {
            suspiciousSilenceDetector.stopMonitoring()
        }
        
        // Cancelar todos los jobs activos para evitar memory leaks
        searchJob?.cancel()
        locationTrackingJob?.cancel()
        routeCalculationJob?.cancel()
        batteryMonitoringJob?.cancel()
        
        // Detener servicios de ubicación
        appModule.locationTrackingService.stopLocationUpdates()
        
        // Detener TTS
        ttsManager?.release()
        ttsManager = null
        
        // CRÍTICO: Detener ForegroundService para Android 10+
        try {
            LocationForegroundService.stop(appModule.appContext)
            SafetyForegroundService.stopService(appModule.appContext)
        } catch (e: Exception) {
            Log.e("NavigationViewModel", "Error stopping foreground service", e)
        }
        
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
        val R = 6371000.0 // Radio de la Tierra en metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
    
    /**
     * Calcula la velocidad actual del usuario en m/s basándose en ubicaciones recientes
     */
    private fun calcularVelocidadActual(ubicacionActual: UbicacionUsuario): Double {
        if (ubicacionesRecientes.size < 2) return 0.0
        
        // Usar las últimas 2 ubicaciones para calcular velocidad instantánea
        val list = ubicacionesRecientes.toList()
        val ultima = list.last()
        val penultima = list[list.size - 2]
        
        val distancia = haversineDistance(
            penultima.latitud, penultima.longitud,
            ultima.latitud, ultima.longitud
        )
        
        // Asumir intervalo de 3 segundos entre actualizaciones
        val tiempoSegundos = 3.0
        
        return distancia / tiempoSegundos
    }
    
    /**
     * Calcula la distancia máxima de snap-to-route según velocidad del usuario
     * - Caminando (<2 m/s): 15m
     * - Corriendo (2-4 m/s): 25m
     * - Coche lento (4-10 m/s): 50m
     * - Coche rápido (>10 m/s): 100m
     */
    private fun calcularMaxDistancePorVelocidad(velocidadMs: Double): Double {
        return when {
            velocidadMs < 2.0 -> 15.0   // Caminando
            velocidadMs < 4.0 -> 25.0   // Corriendo
            velocidadMs < 10.0 -> 50.0  // Coche lento
            else -> 100.0                // Coche rápido
        }
    }

    // FASE 3: Suavizado visual
    private fun lerp(start: Double, end: Double, fraction: Double): Double {
        return start + (end - start) * fraction
    }

    // ==================== TURN-BY-TURN & ARRIVAL LOGIC ====================

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
                val recentLocations = ubicacionesRecientes.toList().takeLast(3)
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
     * Extrae la instrucción real de la lista de instrucciones de la ruta
     */
    private fun calcularInstruccionActual(
        ubicacion: UbicacionUsuario,
        ruta: Ruta,
        remainingDistance: Double
    ): Triple<Instruccion?, Double, Float> {
        if (ruta.instrucciones.isEmpty()) {
            return Triple(
                Instruccion("Continúa hacia tu destino", remainingDistance, TipoManiobra.CONTINUA_RECTO),
                remainingDistance,
                0.5f
            )
        }

        // 1. Encontrar en qué tramo (instrucción) estamos basándonos en la distancia recorrida
        val distanciaRecorrida = (ruta.distanciaMetros - remainingDistance).coerceAtLeast(0.0)
        
        var acumulado = 0.0
        var instruccionActual: Instruccion? = null
        var distanciaAlSiguienteGiro = 0.0
        
        for (inst in ruta.instrucciones) {
            acumulado += inst.distancia
            if (acumulado > distanciaRecorrida) {
                instruccionActual = inst
                distanciaAlSiguienteGiro = acumulado - distanciaRecorrida
                break
            }
        }

        // Si ya pasamos todas las instrucciones, estamos llegando
        if (instruccionActual == null || remainingDistance < 30) {
            return Triple(
                Instruccion("Estás llegando a tu destino", remainingDistance, TipoManiobra.DESTINO),
                remainingDistance,
                1.0f
            )
        }

        // Calcular progreso dentro del tramo actual
        val progresoTramo = if (instruccionActual.distancia > 0) {
            (1.0 - (distanciaAlSiguienteGiro / instruccionActual.distancia)).toFloat()
        } else 1.0f

        return Triple(instruccionActual, distanciaAlSiguienteGiro, progresoTramo.coerceIn(0f, 1f))
    }

    /**
     * RIESGO 2: Procesa las vibraciones de proximidad para giros con Distance Lock
     * Evita vibraciones repetidas cuando el GPS fluctúa
     */
    private fun procesarVibracionesProximidad(distanceToNextTurn: Double, isOffRoute: Boolean) {
        if (!::safeHaptics.isInitialized) return

        // RIESGO 4: Silencio inteligente - no vibrar si está quieto
        if (ubicacionesRecientes.size >= 2) {
            val lastTwo = ubicacionesRecientes.toList().takeLast(2)
            if (lastTwo.size == 2) {
                val movement = haversineDistance(
                    lastTwo[0].latitud, lastTwo[0].longitud,
                    lastTwo[1].latitud, lastTwo[1].longitud
                )
                if (movement < 0.5) return
            }
        }
        
        if (isOffRoute) {
            if (!wasOffRoute) {
                safeHaptics.offRouteDetected()
                wasOffRoute = true
            }
            return
        }

        // Si volvemos a la ruta después de estar fuera
        if (wasOffRoute && !isOffRoute) {
            safeHaptics.resetPriority()
            wasOffRoute = false
        }

        // Sistema de cubetas (buckets) para evitar vibraciones repetidas por ruido de GPS
        val currentBucket = getDistanceBucket(distanceToNextTurn)
        
        if (currentBucket != lastTriggeredDistanceBucket) {
            when (currentBucket) {
                20 -> safeHaptics.turnApproaching20m()
                50 -> safeHaptics.turnApproaching50m()
                100 -> safeHaptics.turnApproaching100m()
            }
            lastTriggeredDistanceBucket = currentBucket
        }
    }

    // ==================== FUNCIONES DE LLEGADA ====================

    /**
     * Compartir llegada exitosa a contacto de confianza
     */
    fun shareArrival() {
        if (::safeHaptics.isInitialized) {
            safeHaptics.arrivalCelebration()
        }
        
        val session = _uiState.value.safeReturnSession
        if (session != null) {
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

    // ==================== MODO REGRESO SEGURO ====================

    /**
     * Inicia un viaje seguro con monitoreo inteligente
     * FASE 1: Activación de viaje
     */
    fun startSafeTrip(
        destination: LugarFavorito,
        estimatedDurationMinutes: Int,
        contacts: List<com.example.regresoacasa.data.safety.EmergencyContact>
    ) {
        val tripId = UUID.randomUUID().toString()
        currentSafeTripId = tripId
        emergencyContacts = contacts

        val ubicacionActual = _uiState.value.ubicacionActual
        if (ubicacionActual == null) {
            _uiState.update {
                it.copy(
                    uiState = UiState.Error("Ubicación no disponible", false)
                )
            }
            return
        }

        // Guardar trip en base de datos
        viewModelScope.launch {
            try {
                val tripEntity = com.example.regresoacasa.data.local.entity.TripEntity(
                    tripId = tripId,
                    startTime = System.currentTimeMillis(),
                    destinationAddress = destination.direccion,
                    destinationLat = destination.latitud,
                    destinationLng = destination.longitud,
                    startLat = ubicacionActual.latitud,
                    startLng = ubicacionActual.longitud,
                    expectedDurationMinutes = estimatedDurationMinutes,
                    status = com.example.regresoacasa.data.local.entity.TripStatus.ACTIVE
                )
                appModule.database.tripDao().insertTrip(tripEntity)
            } catch (e: Exception) {
                Log.e("NavigationViewModel", "Error guardando trip", e)
            }
        }

        // Iniciar SafetyForegroundService
        com.example.regresoacasa.data.safety.SafetyForegroundService.startService(
            appModule.appContext,
            tripId,
            destination.direccion,
            estimatedDurationMinutes,
            contacts
        )

        // Iniciar tracking de ubicación
        iniciarTrackingContinuo()

        // Calcular ruta al destino
        val lugarDestino = Lugar(
            id = destination.id,
            nombre = destination.nombre,
            direccion = destination.direccion,
            latitud = destination.latitud,
            longitud = destination.longitud
        )
        calcularRuta(lugarDestino, "foot-walking")

        _uiState.update {
            it.copy(
                pantallaActual = Pantalla.NAVEGACION,
                isSafeReturnActive = true
            )
        }

        Log.d("NavigationViewModel", "Safe trip started: $tripId")
    }

    /**
     * Detiene el viaje seguro actual
     */
    fun stopSafeTrip() {
        currentSafeTripId?.let { tripId ->
            viewModelScope.launch {
                try {
                    appModule.database.tripDao().updateTripStatus(
                        tripId,
                        com.example.regresoacasa.data.local.entity.TripStatus.COMPLETED.name,
                        System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    Log.e("NavigationViewModel", "Error actualizando trip", e)
                }
            }
        }

        com.example.regresoacasa.data.safety.SafetyForegroundService.stopService(appModule.appContext)
        detenerNavegacion()

        currentSafeTripId = null
        emergencyContacts = emptyList()

        _uiState.update {
            it.copy(
                isSafeReturnActive = false,
                pantallaActual = Pantalla.MAP
            )
        }

        Log.d("NavigationViewModel", "Safe trip stopped")
    }

    /**
     * Botón de pánico - activa emergencia inmediata
     * FASE 6: Botón de pánico real
     */
    fun triggerEmergency() {
        Log.w("NavigationViewModel", "EMERGENCY TRIGGERED")
        
        // Enviar SMS inmediato a contactos
        val location = _uiState.value.ubicacionActual
        val message = if (location != null) {
            "🚨 EMERGENCIA - Regreso Seguro\nNecesito ayuda urgente.\n📍 https://www.google.com/maps?q=${location.latitud},${location.longitud}"
        } else {
            "🚨 EMERGENCIA - Regreso Seguro\nNecesito ayuda urgente."
        }

        viewModelScope.launch {
            emergencyContacts.forEach { contact ->
                try {
                    val smsManager = appModule.appContext.getSystemService(android.telephony.SmsManager::class.java)
                    smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
                    Log.d("NavigationViewModel", "Emergency SMS sent to ${contact.name}")
                } catch (e: Exception) {
                    Log.e("NavigationViewModel", "Error sending emergency SMS", e)
                }
            }
        }

        // Notificar al servicio
        com.example.regresoacasa.data.safety.SafetyForegroundService.triggerEmergency(appModule.appContext)

        // Marcar trip como emergencia
        currentSafeTripId?.let { tripId ->
            viewModelScope.launch {
                try {
                    appModule.database.tripDao().updateTripStatus(
                        tripId,
                        com.example.regresoacasa.data.local.entity.TripStatus.EMERGENCY.name,
                        System.currentTimeMillis()
                    )
                    appModule.database.tripDao().markAlertTriggered(tripId, "CRITICAL")
                } catch (e: Exception) {
                    Log.e("NavigationViewModel", "Error marking emergency", e)
                }
            }
        }

        // Feedback háptico
        if (::safeHaptics.isInitialized) {
            safeHaptics.guardianAlert()
        }

        _uiState.update {
            it.copy(
                uiState = UiState.Error("🚨 Alerta de emergencia enviada", false)
            )
        }
    }

    /**
     * Usuario responde a alerta activa
     */
    fun respondToAlert() {
        // Esto debería ser manejado por el SafetyAlertEngine
        // Por ahora, simplemente logueamos
        Log.d("NavigationViewModel", "User responded to alert")
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
