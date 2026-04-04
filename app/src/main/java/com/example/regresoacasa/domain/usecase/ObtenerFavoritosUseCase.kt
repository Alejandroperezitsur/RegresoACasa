package com.example.regresoacasa.domain.usecase

import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.repository.MapRepository
import kotlinx.coroutines.flow.Flow

class ObtenerFavoritosUseCase(
    private val repository: MapRepository
) {
    operator fun invoke(): Flow<List<LugarFavorito>> {
        return repository.getFavoritos()
    }
}
