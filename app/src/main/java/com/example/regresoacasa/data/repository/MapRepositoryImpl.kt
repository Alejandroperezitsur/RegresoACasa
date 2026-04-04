package com.example.regresoacasa.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.regresoacasa.data.local.LugarFavoritoDao
import com.example.regresoacasa.data.local.entity.LugarFavoritoEntity
import com.example.regresoacasa.data.remote.NominatimApiService
import com.example.regresoacasa.data.remote.OrsApiService
import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.PuntoRuta
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.repository.MapRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
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

    override suspend fun buscarLugares(query: String): Result<List<Lugar>> {
        return try {
            val response = nominatimApi.searchAddress(query)
            if (response.isSuccessful && response.body() != null) {
                val results = response.body()!!.map { result ->
                    Lugar(
                        id = result.placeId?.toString() ?: "",
                        nombre = result.displayName?.substringBefore(",") ?: "",
                        direccion = result.displayName ?: "",
                        latitud = result.lat?.toDoubleOrNull() ?: 0.0,
                        longitud = result.lon?.toDoubleOrNull() ?: 0.0
                    )
                }
                Result.success(results)
            } else {
                Result.failure(Exception("Error al buscar: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun calcularRuta(
        origen: UbicacionUsuario,
        destino: LugarFavorito,
        modo: String
    ): Result<Ruta> {
        return try {
            val start = "${origen.longitud},${origen.latitud}"
            val end = "${destino.longitud},${destino.latitud}"

            val response = if (modo == "driving-car") {
                orsApi.getDrivingRoute(apiKey, start, end)
            } else {
                orsApi.getWalkingRoute(apiKey, start, end)
            }

            if (response.isSuccessful && response.body() != null) {
                val routeResponse = response.body()!!
                if (routeResponse.routes.isNotEmpty()) {
                    val route = routeResponse.routes[0]
                    val puntos = decodificarPolyline(route.geometry)

                    val ruta = Ruta(
                        distanciaMetros = route.summary.distance,
                        duracionSegundos = route.summary.duration,
                        puntos = puntos
                    )
                    Result.success(ruta)
                } else {
                    Result.failure(Exception("No se encontró ruta"))
                }
            } else {
                Result.failure(Exception("Error al calcular ruta: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
}
