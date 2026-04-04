package com.example.regresoacasa.domain.usecase

import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.repository.MapRepository

class GuardarFavoritoUseCase(
    private val repository: MapRepository
) {
    suspend operator fun invoke(favorito: LugarFavorito): Result<Unit> {
        return try {
            repository.guardarFavorito(favorito)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
