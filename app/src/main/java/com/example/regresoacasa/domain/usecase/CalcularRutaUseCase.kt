package com.example.regresoacasa.domain.usecase

import com.example.regresoacasa.domain.model.ApiResult
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.repository.MapRepository

class CalcularRutaUseCase(
    private val repository: MapRepository
) {
    /**
     * Calcula una ruta desde la ubicación proporcionada hasta el destino.
     * 
     * @param origen Ubicación de origen (si es null, el repository manejará el error o el VM debe proveerla)
     * @param destino Destino de la ruta
     * @param modo Modo de transporte ("foot-walking" o "driving-car")
     */
    suspend operator fun invoke(
        origen: UbicacionUsuario,
        destino: UbicacionUsuario,
        modo: String = "foot-walking"
    ): ApiResult<Ruta> {
        return repository.calcularRuta(origen, destino, modo)
    }
}
