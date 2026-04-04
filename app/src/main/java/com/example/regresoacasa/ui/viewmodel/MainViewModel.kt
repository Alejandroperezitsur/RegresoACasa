package com.example.regresoacasa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.regresoacasa.di.AppModule
import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.Ruta
import com.example.regresoacasa.domain.model.UbicacionUsuario
import com.example.regresoacasa.domain.usecase.BuscarLugaresUseCase
import com.example.regresoacasa.domain.usecase.CalcularRutaUseCase
import com.example.regresoacasa.domain.usecase.GuardarFavoritoUseCase
import com.example.regresoacasa.domain.usecase.ObtenerCasaUseCase
import com.example.regresoacasa.domain.usecase.ObtenerFavoritosUseCase
import com.example.regresoacasa.domain.usecase.ObtenerUbicacionUseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(FlowPreview::class)
class MainViewModel(
    private val buscarLugaresUseCase: BuscarLugaresUseCase,
    private val calcularRutaUseCase: CalcularRutaUseCase,
    private val guardarFavoritoUseCase: GuardarFavoritoUseCase,
    private val obtenerCasaUseCase: ObtenerCasaUseCase,
    private val obtenerFavoritosUseCase: ObtenerFavoritosUseCase,
    private val obtenerUbicacionUseCase: ObtenerUbicacionUseCase
) : ViewModel() {

    // UI States
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        cargarCasa()
        setupSearchDebounce()
    }

    private fun setupSearchDebounce() {
        _searchQuery
            .debounce(400)
            .onEach { query ->
                if (query.length >= 3) {
                    buscarLugares(query)
                } else {
                    _uiState.update { it.copy(resultadosBusqueda = emptyList()) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(busqueda = query) }
    }

    private fun buscarLugares(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(estaBuscando = true) }
            buscarLugaresUseCase(query).collect { result ->
                result.fold(
                    onSuccess = { lugares ->
                        _uiState.update {
                            it.copy(
                                resultadosBusqueda = lugares,
                                estaBuscando = false
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                error = "Error al buscar: ${error.message}",
                                estaBuscando = false
                            )
                        }
                    }
                )
            }
        }
    }

    fun cargarCasa() {
        viewModelScope.launch {
            val casa = obtenerCasaUseCase()
            _uiState.update { it.copy(casa = casa) }
        }
    }

    fun obtenerUbicacion() {
        viewModelScope.launch {
            _uiState.update { it.copy(estaCargandoUbicacion = true, error = null) }
            obtenerUbicacionUseCase().fold(
                onSuccess = { ubicacion ->
                    _uiState.update {
                        it.copy(
                            ubicacionActual = ubicacion,
                            estaCargandoUbicacion = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            error = error.message ?: "Error al obtener ubicación",
                            estaCargandoUbicacion = false
                        )
                    }
                }
            )
        }
    }

    fun irACasa(modo: String = "foot-walking") {
        val casa = _uiState.value.casa ?: run {
            _uiState.update { it.copy(error = "Configura tu casa primero") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    estaCalculandoRuta = true,
                    error = null,
                    pantallaActual = Pantalla.NAVEGACION
                )
            }

            calcularRutaUseCase(casa, modo).fold(
                onSuccess = { ruta ->
                    _uiState.update {
                        it.copy(
                            rutaActual = ruta,
                            estaCalculandoRuta = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            error = error.message ?: "Error al calcular ruta",
                            estaCalculandoRuta = false,
                            pantallaActual = Pantalla.MAP
                        )
                    }
                }
            )
        }
    }

    fun guardarCasaDesdeLugar(lugar: Lugar) {
        val favorito = LugarFavorito(
            id = UUID.randomUUID().toString(),
            nombre = "Casa",
            direccion = lugar.direccion,
            latitud = lugar.latitud,
            longitud = lugar.longitud,
            tipo = LugarFavorito.TipoFavorito.CASA
        )

        viewModelScope.launch {
            _uiState.update { it.copy(estaGuardando = true) }
            guardarFavoritoUseCase(favorito).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            casa = favorito,
                            estaGuardando = false,
                            busqueda = "",
                            resultadosBusqueda = emptyList(),
                            pantallaActual = Pantalla.MAP
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            error = error.message ?: "Error al guardar",
                            estaGuardando = false
                        )
                    }
                }
            )
        }
    }

    fun seleccionarLugar(lugar: Lugar) {
        _uiState.update {
            it.copy(
                lugarSeleccionado = lugar,
                busqueda = lugar.direccion,
                resultadosBusqueda = emptyList()
            )
        }
    }

    fun cambiarPantalla(pantalla: Pantalla) {
        _uiState.update { it.copy(pantallaActual = pantalla) }
    }

    fun limpiarError() {
        _uiState.update { it.copy(error = null) }
    }

    fun limpiarRuta() {
        _uiState.update { it.copy(rutaActual = null) }
    }

    class Factory(private val appModule: AppModule) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(
                    buscarLugaresUseCase = appModule.buscarLugaresUseCase,
                    calcularRutaUseCase = appModule.calcularRutaUseCase,
                    guardarFavoritoUseCase = appModule.guardarFavoritoUseCase,
                    obtenerCasaUseCase = appModule.obtenerCasaUseCase,
                    obtenerFavoritosUseCase = appModule.obtenerFavoritosUseCase,
                    obtenerUbicacionUseCase = appModule.obtenerUbicacionUseCase
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class MainUiState(
    val pantallaActual: Pantalla = Pantalla.MAP,
    val ubicacionActual: UbicacionUsuario? = null,
    val casa: LugarFavorito? = null,
    val rutaActual: Ruta? = null,
    val busqueda: String = "",
    val resultadosBusqueda: List<Lugar> = emptyList(),
    val lugarSeleccionado: Lugar? = null,
    val estaCargandoUbicacion: Boolean = false,
    val estaCalculandoRuta: Boolean = false,
    val estaBuscando: Boolean = false,
    val estaGuardando: Boolean = false,
    val error: String? = null
)

enum class Pantalla {
    MAP,
    SEARCH,
    NAVEGACION,
    FAVORITES
}
