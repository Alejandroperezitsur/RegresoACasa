package com.example.regresoacasa.domain.usecase

import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.repository.MapRepository

class CalcularRutaUseCase(
    private val repository: MapRepository
) {
    suspend operator fun invoke(
        destino: LugarFavorito,
        modo: String = "foot-walking"
    ): Result<Ruta> {
        val ubicacion = repository.obtenerUbicacionActual()
            .getOrElse { return Result.failure(it) }

        return repository.calcularRuta(ubicacion, destino, modo)
    }
}
