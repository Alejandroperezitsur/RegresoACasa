package com.example.regresoacasa.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.regresoacasa.data.local.LugarFavoritoDao
import com.example.regresoacasa.data.local.entity.LugarFavoritoEntity
import com.example.regresoacasa.data.remote.NominatimApiService
import com.example.regresoacasa.data.remote.OrsApiService
import com.example.regresoacasa.domain.model.ApiError
import com.example.regresoacasa.domain.model.ApiResult
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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
    private val nominatimApi: NominatimApiService,
    private val orsApi: OrsApiService,
    private val apiKey: String
) : MapRepository {

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
        return retryWithBackoff(maxRetries = 3, initialDelay = 1000) {
            try {
                withTimeout(30000) { // 30 segundos timeout
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
                                Lugar(
                                    id = result.placeId?.toString() ?: "",
                                    nombre = result.displayName?.substringBefore(",") ?: "",
                                    direccion = result.displayName ?: "",
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
                ApiResult.Error(ApiError.NoInternet)
            } catch (e: SocketTimeoutException) {
                Log.e("MapRepository", "Request timeout", e)
                ApiResult.Error(ApiError.Timeout)
            } catch (e: RateLimitException) {
                Log.e("MapRepository", "Rate limit exceeded", e)
                throw e // Re-throw to trigger retry
            } catch (e: Exception) {
                Log.e("MapRepository", "Error searching places", e)
                ApiResult.Error(ApiError.Unknown(e))
            }
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
        destino: LugarFavorito,
        modo: String
    ): ApiResult<Ruta> {
        // Validar API key antes de hacer la llamada
        if (apiKey.isBlank()) {
            Log.e("MapRepository", "ORS API key is missing")
            return ApiResult.Error(ApiError.InvalidApiKey)
        }
        
        return retryWithBackoff(maxRetries = 3, initialDelay = 1000) {
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
                        response.body()!!.routes.isEmpty() -> {
                            ApiResult.Error(ApiError.NotFound)
                        }
                        else -> {
                            val routeResponse = response.body()!!
                            val route = routeResponse.routes[0]
                            
                            // Validar polyline antes de decodificar
                            if (route.geometry.isBlank()) {
                                return@withTimeout ApiResult.Error(ApiError.InvalidResponse)
                            }
                            
                            val puntos = try {
                                decodificarPolyline(route.geometry)
                            } catch (e: Exception) {
                                Log.e("MapRepository", "Error decoding polyline", e)
                                return@withTimeout ApiResult.Error(ApiError.InvalidResponse)
                            }

                            // Extraer y traducir instrucciones de los steps
                            val instrucciones = route.segments.flatMap { segment ->
                                segment.steps.map { step ->
                                    InstruccionNavegacion(
                                        texto = InstructionTranslator.translate(step.instruction),
                                        distancia = step.distance,
                                        tipo = mapTypeToManiobra(step.type),
                                        nombreCalle = step.name ?: ""
                                    )
                                }
                            }

                            val ruta = Ruta(
                                distanciaMetros = route.summary.distance,
                                duracionSegundos = route.summary.duration,
                                puntos = puntos,
                                instrucciones = instrucciones
                            )
                            ApiResult.Success(ruta)
                        }
                    }
                }
            } catch (e: UnknownHostException) {
                Log.e("MapRepository", "No internet connection", e)
                ApiResult.Error(ApiError.NoInternet)
            } catch (e: SocketTimeoutException) {
                Log.e("MapRepository", "Request timeout", e)
                ApiResult.Error(ApiError.Timeout)
            } catch (e: RateLimitException) {
                Log.e("MapRepository", "Rate limit exceeded", e)
                throw e // Re-throw to trigger retry
            } catch (e: Exception) {
                Log.e("MapRepository", "Error calculating route", e)
                ApiResult.Error(ApiError.Unknown(e))
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
                            UbicacionUsuario(
                                latitud = location.latitude,
                                longitud = location.longitude,
                                precision = location.accuracy
                            )
                        )
                    } else {
                        continuation.resumeWithException(Exception("No se pudo obtener ubicación"))
                    }
                }

                task.addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
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
            0 -> TipoManiobra.CONTINUA_RECTO  // Continue straight
            1 -> TipoManiobra.GIRA_DERECHA     // Turn slight right
            2 -> TipoManiobra.GIRA_IZQUIERDA   // Turn slight left
            3 -> TipoManiobra.GIRA_DERECHA     // Turn right
            4 -> TipoManiobra.GIRA_IZQUIERDA   // Turn left
            5 -> TipoManiobra.GIRA_DERECHA     // Turn sharp right
            6 -> TipoManiobra.GIRA_IZQUIERDA   // Turn sharp left
            7 -> TipoManiobra.MEDIA_VUELTA     // U-turn
            8, 9 -> TipoManiobra.ROTONDA       // Roundabout (enter/exit)
            10 -> TipoManiobra.DESTINO         // Arrive at destination
            else -> TipoManiobra.CONTINUA_RECTO
        }
    }
}
