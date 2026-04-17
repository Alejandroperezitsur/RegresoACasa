package com.example.regresoacasa.domain.repository

import com.example.regresoacasa.domain.model.ApiResult
import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario
import kotlinx.coroutines.flow.Flow

interface MapRepository {
    // Favoritos
    fun getFavoritos(): Flow<List<LugarFavorito>>
    suspend fun getCasa(): LugarFavorito?
    suspend fun getTrabajo(): LugarFavorito?
    suspend fun guardarFavorito(favorito: LugarFavorito)
    suspend fun eliminarFavorito(id: String)

    // Búsqueda
    suspend fun buscarLugares(query: String): ApiResult<List<Lugar>>
    suspend fun obtenerDireccion(lat: Double, lon: Double): ApiResult<Lugar>

    // Rutas
    suspend fun calcularRuta(
        origen: UbicacionUsuario,
        destino: LugarFavorito,
        modo: String
    ): ApiResult<Ruta>

    // Ubicación
    suspend fun obtenerUbicacionActual(): Result<UbicacionUsuario>
}
