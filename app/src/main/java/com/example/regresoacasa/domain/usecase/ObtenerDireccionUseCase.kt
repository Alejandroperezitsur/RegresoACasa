package com.example.regresoacasa.domain.usecase

import com.example.regresoacasa.domain.model.ApiResult
import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.repository.MapRepository

class ObtenerDireccionUseCase(private val repository: MapRepository) {
    suspend operator fun invoke(lat: Double, lon: Double): ApiResult<Lugar> {
        return repository.obtenerDireccion(lat, lon)
    }
}
