package com.example.regresoacasa.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.regresoacasa.data.local.PreferencesManager
import com.example.regresoacasa.data.model.Route
import com.example.regresoacasa.data.remote.RetrofitClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefsManager = PreferencesManager(context)
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _ubicacionActual = MutableStateFlow<GeoPoint?>(null)
    val ubicacionActual: StateFlow<GeoPoint?> = _ubicacionActual.asStateFlow()

    private val _casaUbicacion = MutableStateFlow<GeoPoint?>(null)
    val casaUbicacion: StateFlow<GeoPoint?> = _casaUbicacion.asStateFlow()

    private val _casaDireccion = MutableStateFlow<String?>(null)
    val casaDireccion: StateFlow<String?> = _casaDireccion.asStateFlow()

    private val _rutaPolilinea = MutableStateFlow<Polyline?>(null)
    val rutaPolilinea: StateFlow<Polyline?> = _rutaPolilinea.asStateFlow()

    private val _rutaInfo = MutableStateFlow<Route?>(null)
    val rutaInfo: StateFlow<Route?> = _rutaInfo.asStateFlow()

    private val _estaCargando = MutableStateFlow(false)
    val estaCargando: StateFlow<Boolean> = _estaCargando.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _apiKeyConfigurada = MutableStateFlow(prefsManager.obtenerOrsApiKey().isNotEmpty())
    val apiKeyConfigurada: StateFlow<Boolean> = _apiKeyConfigurada.asStateFlow()

    init {
        cargarCasaGuardada()
    }

    private fun cargarCasaGuardada() {
        val lat = prefsManager.obtenerCasaLat()
        val lng = prefsManager.obtenerCasaLng()
        val direccion = prefsManager.obtenerCasaDireccion()

        if (lat != null && lng != null) {
            _casaUbicacion.value = GeoPoint(lat, lng)
            _casaDireccion.value = direccion
        }
    }

    @SuppressLint("MissingPermission")
    fun obtenerUbicacionActual() {
        if (!tienePermisoUbicacion()) {
            _error.value = "Se requiere permiso de ubicación"
            return
        }

        _estaCargando.value = true
        _error.value = null

        try {
            val cancellationToken = CancellationTokenSource()
            val locationTask = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            )

            locationTask.addOnSuccessListener { location ->
                if (location != null) {
                    _ubicacionActual.value = GeoPoint(location.latitude, location.longitude)
                    Log.d("MapViewModel", "Ubicación obtenida: ${location.latitude}, ${location.longitude}")
                } else {
                    _error.value = "No se pudo obtener la ubicación"
                }
                _estaCargando.value = false
            }

            locationTask.addOnFailureListener { exception ->
                _error.value = "Error al obtener ubicación: ${exception.message}"
                _estaCargando.value = false
            }

            locationTask.addOnCompleteListener {
                if (_estaCargando.value) {
                    _estaCargando.value = false
                }
            }
        } catch (e: Exception) {
            _error.value = "Error: ${e.message}"
            _estaCargando.value = false
        }
    }

    fun buscarYGuardarCasa(direccion: String) {
        viewModelScope.launch {
            _estaCargando.value = true
            _error.value = null

            try {
                // Usar API key hardcodeada por defecto
                val apiKey = prefsManager.obtenerOrsApiKey()

                val response = RetrofitClient.orsApiService.geocodeAddress(
                    apiKey = apiKey,
                    text = direccion
                )

                if (response.isSuccessful && response.body() != null) {
                    val geocodingResponse = response.body()!!
                    if (geocodingResponse.features.isNotEmpty()) {
                        val feature = geocodingResponse.features[0]
                        val coords = feature.geometry.coordinates
                        val lng = coords[0]
                        val lat = coords[1]

                        prefsManager.guardarCasa(lat, lng, direccion)
                        _casaUbicacion.value = GeoPoint(lat, lng)
                        _casaDireccion.value = direccion
                        _estaCargando.value = false
                    } else {
                        _error.value = "No se encontró la dirección"
                        _estaCargando.value = false
                    }
                } else {
                    _error.value = "Error al buscar dirección: ${response.code()}"
                    _estaCargando.value = false
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
                _estaCargando.value = false
            }
        }
    }

    fun calcularRutaATipo(modo: String = "foot-walking") {
        val ubicacion = _ubicacionActual.value
        val casa = _casaUbicacion.value
        val apiKey = prefsManager.obtenerOrsApiKey()

        if (ubicacion == null) {
            _error.value = "Primero obtén tu ubicación actual"
            return
        }

        if (casa == null) {
            _error.value = "Configura tu dirección de casa primero"
            return
        }

        if (apiKey == null) {
            _error.value = "Configura tu API key de OpenRouteService"
            return
        }

        viewModelScope.launch {
            _estaCargando.value = true
            _error.value = null

            try {
                val start = "${ubicacion.longitude},${ubicacion.latitude}"
                val end = "${casa.longitude},${casa.latitude}"

                val response = if (modo == "driving-car") {
                    RetrofitClient.orsApiService.getDrivingRoute(apiKey, start, end)
                } else {
                    RetrofitClient.orsApiService.getWalkingRoute(apiKey, start, end)
                }

                if (response.isSuccessful && response.body() != null) {
                    val routeResponse = response.body()!!
                    if (routeResponse.routes.isNotEmpty()) {
                        val route = routeResponse.routes[0]
                        _rutaInfo.value = route

                        val polyline = crearPolilineaDesdeGeometry(route.geometry)
                        _rutaPolilinea.value = polyline

                        Log.d("MapViewModel", "Ruta calculada: ${route.summary.distance}m")
                    } else {
                        _error.value = "No se encontró ruta"
                    }
                } else {
                    _error.value = "Error al calcular ruta: ${response.code()}"
                }
                _estaCargando.value = false
            } catch (e: Exception) {
                _error.value = "Error al calcular ruta: ${e.message}"
                _estaCargando.value = false
            }
        }
    }

    private fun crearPolilineaDesdeGeometry(geometry: String): Polyline {
        val polyline = Polyline()
        val puntos = decodificarPolyline(geometry)
        polyline.setPoints(puntos)
        polyline.outlinePaint.strokeWidth = 10f
        polyline.outlinePaint.color = android.graphics.Color.BLUE
        return polyline
    }

    private fun decodificarPolyline(encoded: String): List<GeoPoint> {
        val poly = ArrayList<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = GeoPoint(lat / 1E5, lng / 1E5)
            poly.add(p)
        }
        return poly
    }

    fun limpiarError() {
        _error.value = null
    }

    fun limpiarRuta() {
        _rutaPolilinea.value = null
        _rutaInfo.value = null
    }

    fun borrarCasa() {
        prefsManager.borrarCasa()
        _casaUbicacion.value = null
        _casaDireccion.value = null
        limpiarRuta()
    }

    private fun tienePermisoUbicacion(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun formatearDistancia(distanciaMetros: Double): String {
        return if (distanciaMetros >= 1000) {
            "%.2f km".format(distanciaMetros / 1000)
        } else {
            "%.0f m".format(distanciaMetros)
        }
    }

    fun formatearDuracion(duracionSegundos: Double): String {
        val minutos = (duracionSegundos / 60).toInt()
        val horas = minutos / 60
        val minsRestantes = minutos % 60

        return if (horas > 0) {
            "$horas h ${minsRestantes} min"
        } else {
            "$minsRestantes min"
        }
    }

    fun setGpsDisabled() {
        _error.value = "GPS desactivado. Activa la ubicación para continuar."
    }

    fun setPermissionDenied() {
        _error.value = "Permiso de ubicación denegado. La app necesita acceder a tu ubicación."
    }

    fun refreshLocation() {
        // Refrescar ubicación cuando la app vuelve a primer plano
        if (tienePermisoUbicacion()) {
            obtenerUbicacionActual()
        }
    }

    // NUEVAS FUNCIONES PARA SELECCIÓN DE CASA EN MAPA

    suspend fun obtenerDireccionDeCoordenadas(lat: Double, lng: Double): String {
        return try {
            val response = RetrofitClient.nominatimApiService.reverseGeocode(lat, lng)
            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                result.displayName ?: result.address?.formatAddress() ?: "Ubicación seleccionada"
            } else {
                "${String.format("%.5f", lat)}, ${String.format("%.5f", lng)}"
            }
        } catch (e: Exception) {
            Log.e("MapViewModel", "Error reverse geocoding: ${e.message}")
            "${String.format("%.5f", lat)}, ${String.format("%.5f", lng)}"
        }
    }

    suspend fun buscarDireccion(query: String): Triple<Double, Double, String>? {
        _estaCargando.value = true
        _error.value = null
        
        return try {
            // Primero intentar con Nominatim (gratuito, sin API key)
            val nominatimResponse = RetrofitClient.nominatimApiService.searchAddress(query)
            
            if (nominatimResponse.isSuccessful && nominatimResponse.body() != null) {
                val results = nominatimResponse.body()!!
                if (results.isNotEmpty()) {
                    val result = results[0]
                    val lat = result.lat?.toDoubleOrNull() ?: 0.0
                    val lng = result.lon?.toDoubleOrNull() ?: 0.0
                    val address = result.displayName ?: result.address?.formatAddress() ?: query
                    
                    _estaCargando.value = false
                    return Triple(lat, lng, address)
                }
            }
            
            // Fallback a OpenRouteService si Nominatim falla
            val apiKey = prefsManager.obtenerOrsApiKey()
            val orsResponse = RetrofitClient.orsApiService.geocodeAddress(apiKey, query)
            
            if (orsResponse.isSuccessful && orsResponse.body() != null) {
                val geocodingResponse = orsResponse.body()!!
                if (geocodingResponse.features.isNotEmpty()) {
                    val feature = geocodingResponse.features[0]
                    val coords = feature.geometry.coordinates
                    val lng = coords[0]
                    val lat = coords[1]
                    
                    _estaCargando.value = false
                    return Triple(lat, lng, query)
                }
            }
            
            _error.value = "No se encontró la dirección"
            _estaCargando.value = false
            null
            
        } catch (e: Exception) {
            Log.e("MapViewModel", "Error buscando dirección: ${e.message}")
            _error.value = "Error al buscar dirección: ${e.message}"
            _estaCargando.value = false
            null
        }
    }

    fun guardarCasaDesdeMapa(lat: Double, lng: Double, direccion: String) {
        viewModelScope.launch {
            _estaCargando.value = true
            _error.value = null
            
            try {
                prefsManager.guardarCasa(lat, lng, direccion)
                _casaUbicacion.value = GeoPoint(lat, lng)
                _casaDireccion.value = direccion
                _estaCargando.value = false
                Log.d("MapViewModel", "Casa guardada: $direccion ($lat, $lng)")
            } catch (e: Exception) {
                _error.value = "Error al guardar casa: ${e.message}"
                _estaCargando.value = false
            }
        }
    }
}
