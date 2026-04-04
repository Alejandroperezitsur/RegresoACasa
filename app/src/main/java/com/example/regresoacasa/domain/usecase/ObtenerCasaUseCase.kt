package com.example.regresoacasa.domain.usecase

import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.repository.MapRepository

class ObtenerCasaUseCase(
    private val repository: MapRepository
) {
    suspend operator fun invoke(): LugarFavorito? {
        return repository.getCasa()
    }
}
