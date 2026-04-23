package com.example.regresoacasa.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.regresoacasa.data.local.CachedRouteDao
import com.example.regresoacasa.data.local.LugarFavoritoDao
import com.example.regresoacasa.data.local.entity.CachedRouteEntity
import com.example.regresoacasa.data.local.entity.LugarFavoritoEntity
import com.example.regresoacasa.data.remote.NominatimApiService
import com.example.regresoacasa.data.remote.OrsApiService
import com.example.regresoacasa.domain.model.ApiError
import com.example.regresoacasa.domain.model.ApiResult
import com.example.regresoacasa.domain.model.AppError
import com.example.regresoacasa.domain.model.InstruccionNavegacion
import com.example.regresoacasa.domain.model.TipoManiobra
import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.PuntoRuta
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.model.toApiResult
import com.example.regresoacasa.domain.repository.MapRepository
import com.example.regresoacasa.domain.utils.InstructionTranslator
import com.example.regresoacasa.domain.utils.retryWithBackoffOnError
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MapRepositoryImpl(
    private val context: Context,
    private val lugarFavoritoDao: LugarFavoritoDao,
    private val cachedRouteDao: CachedRouteDao,
    private val nominatimApi: NominatimApiService,
    private val orsApi: OrsApiService,
    private val apiKey: String
) : MapRepository {
    
    private val gson = Gson()
    private val ROUTE_CACHE_TTL = 24 * 60 * 60 * 1000L // 24 hours

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    override fun getFavoritos(): Flow<List<LugarFavorito>> {
        return lugarFavoritoDao.getAllFavoritos().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCasa(): LugarFavorito? {
        return lugarFavoritoDao.getCasa()?.toDomain()
    }

    override suspend fun getTrabajo(): LugarFavorito? {
        return lugarFavoritoDao.getTrabajo()?.toDomain()
    }

    override suspend fun guardarFavorito(favorito: LugarFavorito) {
        lugarFavoritoDao.insertFavorito(favorito.toEntity())
    }

    override suspend fun eliminarFavorito(id: String) {
        lugarFavoritoDao.deleteFavoritoById(id)
    }

    override suspend fun buscarLugares(query: String): ApiResult<List<Lugar>> {
        return retryWithBackoffOnError(
            times = 3,
            initialDelay = 1000,
            factor = 2.0,
            shouldRetry = { it is UnknownHostException || it is SocketTimeoutException }
        ) {
            try {
                withTimeout(30000) {
                    val response = nominatimApi.searchAddress(query)
                    
                    when {
                        !response.isSuccessful -> {
                            val error = handleApiError<List<Lugar>>(response)
                            if (error is ApiResult.Error && error.error is ApiError.RateLimited) {
                                throw RateLimitException((error.error as ApiError.RateLimited).retryAfter)
                            }
                            error
                        }
                        response.body() == null -> {
                            ApiResult.Error(ApiError.EmptyResponse)
                        }
                        else -> {
                            val results = response.body()!!.map { result ->
                                val nombrePrincipal = result.displayName?.substringBefore(",") ?: "Sin nombre"
                                val direccionCompleta = result.displayName ?: "Dirección no disponible"
                                
                                Lugar(
                                    id = result.placeId?.toString() ?: "",
                                    nombre = nombrePrincipal,
                                    direccion = direccionCompleta,
                                    latitud = result.lat?.toDoubleOrNull() ?: 0.0,
                                    longitud = result.lon?.toDoubleOrNull() ?: 0.0
                                )
                            }
                            if (results.isEmpty()) {
                                ApiResult.Error(ApiError.NotFound)
                            } else {
                                ApiResult.Success(results)
                            }
                        }
                    }
                }
            } catch (e: UnknownHostException) {
                Log.e("MapRepository", "No internet connection", e)
                throw AppError.NoInternet
            } catch (e: SocketTimeoutException) {
                Log.e("MapRepository", "Request timeout", e)
                throw AppError.Timeout()
            } catch (e: RateLimitException) {
                Log.e("MapRepository", "Rate limit exceeded", e)
                throw e
            } catch (e: AppError) {
                throw e
            } catch (e: Exception) {
                Log.e("MapRepository", "Error searching places", e)
                throw AppError.Unknown(e.message ?: "Unknown error", e)
            }
        }
    }

    override suspend fun obtenerDireccion(lat: Double, lon: Double): ApiResult<Lugar> {
        return try {
            val response = nominatimApi.reverseGeocode(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                ApiResult.Success(
                    Lugar(
                        id = result.placeId?.toString() ?: "",
                        nombre = "Ubicación seleccionada",
                        direccion = result.displayName ?: "Dirección desconocida",
                        latitud = lat,
                        longitud = lon
                    )
                )
            } else {
                ApiResult.Error(ApiError.NotFound)
            }
        } catch (e: Exception) {
            ApiResult.Error(ApiError.Unknown(e))
        }
    }
    
    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelay: Long = 1000,
        block: suspend () -> ApiResult<T>
    ): ApiResult<T> {
        var currentRetry = 0
        var delay = initialDelay
        
        while (currentRetry < maxRetries) {
            try {
                val result = block()
                if (result is ApiResult.Success) {
                    return result
                }
                // Si es un error que no es rate limit, no reintentar
                if (result is ApiResult.Error && result.error !is ApiError.RateLimited) {
                    return result
                }
            } catch (e: RateLimitException) {
                // Esperar y reintentar
                if (currentRetry == maxRetries - 1) {
                    return ApiResult.Error(ApiError.RateLimited(e.retryAfter))
                }
                delay(delay)
                delay = (delay * 2).coerceAtMost(10000) // Max 10 segundos
                currentRetry++
            }
        }
        
        return ApiResult.Error(ApiError.RateLimited(delay))
    }
    
    private class RateLimitException(val retryAfter: Long) : Exception("Rate limited, retry after ${retryAfter}ms")
    
    private fun <T> handleApiError(response: Response<*>): ApiResult<T> {
        return when (response.code()) {
            401 -> ApiResult.Error(ApiError.InvalidApiKey)
            429 -> {
                val retryAfter = response.headers()["Retry-After"]?.toLongOrNull()?.times(1000) ?: 1000
                ApiResult.Error(ApiError.RateLimited(retryAfter))
            }
            400 -> ApiResult.Error(ApiError.BadRequest)
            404 -> ApiResult.Error(ApiError.NotFound)
            in 500..599 -> ApiResult.Error(ApiError.ServerError)
            else -> ApiResult.Error(ApiError.Unknown(Exception("HTTP ${response.code()}")))
        }
    }

    override suspend fun calcularRuta(
        origen: UbicacionUsuario,
        destino: UbicacionUsuario,
        modo: String
    ): ApiResult<Ruta> {
        // Validar API key antes de hacer la llamada
        if (apiKey.isBlank() || apiKey.length < 60) {
            Log.e("MapRepository", "ORS API key is missing or invalid (too short: ${apiKey.length})")
            return ApiResult.Error(ApiError.InvalidApiKey)
        }
        
        // Check cache first
        val cacheKey = CachedRouteEntity.generateKey(modo, origen.latitud, origen.longitud, destino.latitud, destino.longitud)
        val cachedRoute = cachedRouteDao.getRouteByKey(cacheKey, System.currentTimeMillis() - ROUTE_CACHE_TTL)
        if (cachedRoute != null) {
            Log.d("MapRepository", "Using cached route for $cacheKey")
            try {
                val ruta = gson.fromJson(cachedRoute.data, Ruta::class.java)
                return ApiResult.Success(ruta)
            } catch (e: Exception) {
                Log.e("MapRepository", "Error parsing cached route", e)
                // Continue to fetch from API
            }
        }
        
        // Check for similar route (within 100m)
        val similarRoute = cachedRouteDao.findSimilarRoute(
            origen.latitud, origen.longitud,
            destino.latitud, destino.longitud,
            System.currentTimeMillis() - ROUTE_CACHE_TTL
        )
        if (similarRoute != null) {
            Log.d("MapRepository", "Using similar cached route")
            try {
                val ruta = gson.fromJson(similarRoute.data, Ruta::class.java)
                return ApiResult.Success(ruta)
            } catch (e: Exception) {
                Log.e("MapRepository", "Error parsing similar cached route", e)
            }
        }
        
        return retryWithBackoffOnError(
            times = 3,
            initialDelay = 1000,
            factor = 2.0,
            shouldRetry = { it is UnknownHostException || it is SocketTimeoutException }
        ) {
            try {
                withTimeout(30000) { // 30 segundos timeout
                    val start = "${origen.longitud},${origen.latitud}"
                    val end = "${destino.longitud},${destino.latitud}"

                    Log.d("MapRepository", "Calculando ruta: $start -> $end (modo: $modo)")

                    val response = if (modo == "driving-car") {
                        orsApi.getDrivingRoute(apiKey, start, end)
                    } else {
                        orsApi.getWalkingRoute(apiKey, start, end)
                    }

                    when {
                        !response.isSuccessful -> {
                            val error = handleApiError<Ruta>(response)
                            if (error is ApiResult.Error && error.error is ApiError.RateLimited) {
                                throw RateLimitException((error.error as ApiError.RateLimited).retryAfter)
                            }
                            error
                        }
                        response.body() == null -> {
                            ApiResult.Error(ApiError.EmptyResponse)
                        }
                        response.body()!!.features.isEmpty() -> {
                            ApiResult.Error(ApiError.NotFound)
                        }
                        else -> {
                            val routeResponse = response.body()!!
                            val feature = routeResponse.features[0]
                            val geometry = feature.geometry
                            
                            // Validar que tengamos coordenadas
                            if (geometry.coordinates.isEmpty()) {
                                return@withTimeout ApiResult.Error(ApiError.InvalidResponse)
                            }
                            
                            // En GeoJSON las coordenadas ya vienen como una lista de [lon, lat]
                            val puntos = geometry.coordinates.map { 
                                PuntoRuta(it[1], it[0]) 
                            }

                            // Extraer y traducir instrucciones de los steps de las properties del feature
                            val instrucciones = feature.properties.segments.flatMap { segment ->
                                segment.steps.map { step ->
                                    com.example.regresoacasa.domain.model.Instruccion(
                                        texto = InstructionTranslator.translate(step.instruction),
                                        distancia = step.distance,
                                        tipo = mapTypeToManiobra(step.type)
                                    )
                                }
                            }

                            // LOG para depuración
                            Log.d("MapRepository", "Ruta procesada con ${puntos.size} puntos e ${instrucciones.size} instrucciones")
                            if (puntos.isNotEmpty()) {
                                Log.d("MapRepository", "Primer punto: ${puntos[0].latitud}, ${puntos[0].longitud}")
                            }

                            val ruta = Ruta(
                                distanciaMetros = feature.properties.summary.distance,
                                duracionSegundos = feature.properties.summary.duration,
                                puntos = puntos,
                                instrucciones = instrucciones
                            )
                            
                            // Cache the route
                            try {
                                val routeJson = gson.toJson(ruta)
                                val cachedEntity = CachedRouteEntity(
                                    key = cacheKey,
                                    data = routeJson,
                                    timestamp = System.currentTimeMillis(),
                                    profile = modo,
                                    startLat = origen.latitud,
                                    startLng = origen.longitud,
                                    endLat = destino.latitud,
                                    endLng = destino.longitud
                                )
                                cachedRouteDao.insertRoute(cachedEntity)
                            } catch (e: Exception) {
                                Log.e("MapRepository", "Error caching route", e)
                            }
                            
                            ApiResult.Success(ruta)
                        }
                    }
                }
            } catch (e: UnknownHostException) {
                Log.e("MapRepository", "No internet connection", e)
                throw AppError.NoInternet
            } catch (e: SocketTimeoutException) {
                Log.e("MapRepository", "Request timeout", e)
                throw AppError.Timeout()
            } catch (e: RateLimitException) {
                Log.e("MapRepository", "Rate limit exceeded", e)
                throw e // Re-throw to trigger retry
            } catch (e: AppError) {
                throw e
            } catch (e: Exception) {
                Log.e("MapRepository", "Error calculating route", e)
                throw AppError.Unknown(e.message ?: "Unknown error", e)
            }
        }
    }

    @Suppress("MissingPermission")
    override suspend fun obtenerUbicacionActual(): Result<UbicacionUsuario> {
        if (!tienePermisoUbicacion()) {
            return Result.failure(Exception("Permiso de ubicación requerido"))
        }

        return try {
            suspendCancellableCoroutine { continuation ->
                val cancellationToken = CancellationTokenSource()
                val task = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationToken.token
                )

                task.addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(
                            Result.success(
                                UbicacionUsuario(
                                    latitud = location.latitude,
                                    longitud = location.longitude,
                                    precision = location.accuracy
                                )
                            )
                        )
                    } else {
                        continuation.resume(Result.failure(Exception("No se pudo obtener ubicación")))
                    }
                }

                task.addOnFailureListener { exception ->
                    continuation.resume(Result.failure(exception))
                }

                continuation.invokeOnCancellation {
                    cancellationToken.cancel()
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun tienePermisoUbicacion(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun decodificarPolyline(encoded: String): List<PuntoRuta> {
        val poly = ArrayList<PuntoRuta>()
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

            poly.add(PuntoRuta(lat / 1E5, lng / 1E5))
        }
        return poly
    }

    private fun LugarFavoritoEntity.toDomain(): LugarFavorito {
        return LugarFavorito(
            id = id,
            nombre = nombre,
            direccion = direccion,
            latitud = latitud,
            longitud = longitud,
            tipo = LugarFavorito.TipoFavorito.valueOf(tipo)
        )
    }

    private fun LugarFavorito.toEntity(): LugarFavoritoEntity {
        return LugarFavoritoEntity(
            id = id,
            nombre = nombre,
            direccion = direccion,
            latitud = latitud,
            longitud = longitud,
            tipo = tipo.name
        )
    }

    /**
     * Mapea el tipo de instrucción de OpenRouteService a TipoManiobra
     * Basado en: https://github.com/GIScience/openrouteservice-docs/blob/master/README.md
     */
    private fun mapTypeToManiobra(type: Int): TipoManiobra {
        return when (type) {
            0 -> TipoManiobra.CONTINUA_RECTO  // Left
            1 -> TipoManiobra.GIRA_DERECHA     // Right
            2 -> TipoManiobra.GIRA_IZQUIERDA   // Sharp left
            3 -> TipoManiobra.GIRA_DERECHA     // Sharp right
            4 -> TipoManiobra.GIRA_IZQUIERDA   // Slight left
            5 -> TipoManiobra.GIRA_DERECHA     // Slight right
            6 -> TipoManiobra.CONTINUA_RECTO   // Straight
            7 -> TipoManiobra.ROTONDA          // Enter roundabout
            8 -> TipoManiobra.ROTONDA          // Exit roundabout
            9 -> TipoManiobra.MEDIA_VUELTA     // U-turn
            10 -> TipoManiobra.DESTINO         // Goal
            11 -> TipoManiobra.CONTINUA_RECTO  // Depart
            12 -> TipoManiobra.CONTINUA_RECTO  // Keep left
            13 -> TipoManiobra.CONTINUA_RECTO  // Keep right
            else -> TipoManiobra.CONTINUA_RECTO
        }
    }
}
