package com.example.regresoacasa.domain.usecase

import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.repository.MapRepository

class ObtenerUbicacionUseCase(
    private val repository: MapRepository
) {
    suspend operator fun invoke(): Result<UbicacionUsuario> {
        return repository.obtenerUbicacionActual()
    }
}
