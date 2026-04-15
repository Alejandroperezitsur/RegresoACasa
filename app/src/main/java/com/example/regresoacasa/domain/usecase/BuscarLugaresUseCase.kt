package com.example.regresoacasa.domain.usecase

import com.example.regresoacasa.domain.model.ApiResult
import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.repository.MapRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class BuscarLugaresUseCase(
    private val repository: MapRepository
) {
    /**
     * Busca lugares con debounce implícito en el ViewModel.
     * Rate limiting: 1 req/segundo para respetar políticas de Nominatim.
     */
    operator fun invoke(query: String): Flow<ApiResult<List<Lugar>>> = flow {
        if (query.length < 2) {
            emit(ApiResult.Success(emptyList()))
            return@flow
        }
        emit(repository.buscarLugares(query))
    }
}
