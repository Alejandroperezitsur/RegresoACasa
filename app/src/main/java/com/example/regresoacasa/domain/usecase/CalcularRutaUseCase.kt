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
     * La ubicación se pasa como parámetro para mantener el principio de inyección de dependencias
     * y permitir testing más fácil.
     * 
     * @param ubicacionOrigen Ubicación actual del usuario
     * @param destino Destino de la ruta
     * @param modo Modo de transporte ("foot-walking" o "driving-car")
     */
    suspend operator fun invoke(
        ubicacionOrigen: UbicacionUsuario,
        destino: LugarFavorito,
        modo: String = "foot-walking"
    ): ApiResult<Ruta> {
        return repository.calcularRuta(ubicacionOrigen, destino, modo)
    }
}
