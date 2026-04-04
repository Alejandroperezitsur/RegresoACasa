package com.example.regresoacasa.domain.usecase

import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.repository.MapRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class BuscarLugaresUseCase(
    private val repository: MapRepository
) {
    operator fun invoke(query: String): Flow<Result<List<Lugar>>> = flow {
        if (query.length < 3) {
            emit(Result.success(emptyList()))
            return@flow
        }
        emit(repository.buscarLugares(query))
    }
}
