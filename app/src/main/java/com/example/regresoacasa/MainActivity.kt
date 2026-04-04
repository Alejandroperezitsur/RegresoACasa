package com.example.regresoacasa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.regresoacasa.di.AppModule
import com.example.regresoacasa.ui.screens.MainScreen
import com.example.regresoacasa.ui.screens.NavigationScreen
import com.example.regresoacasa.ui.screens.SearchScreen
import com.example.regresoacasa.ui.theme.RegresoACasaTheme
import com.example.regresoacasa.ui.viewmodel.NavigationViewModel
import com.example.regresoacasa.ui.state.Pantalla

class MainActivity : ComponentActivity() {

    private val appModule by lazy { AppModule.getInstance(this) }
    
    private val viewModel: NavigationViewModel by viewModels {
        NavigationViewModel.Factory(appModule)
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                viewModel.obtenerUbicacionUnica()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                viewModel.obtenerUbicacionUnica()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Inicializar ViewModel con Context para acceder a servicios del sistema
        viewModel.initializeWithContext(this)
        
        setContent {
            RegresoACasaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState = viewModel.uiState.collectAsState().value
                    
                    when (uiState.pantallaActual) {
                        Pantalla.MAP -> {
                            MainScreen(
                                viewModel = viewModel,
                                onRequestPermission = { requestLocationPermission() },
                                onIrACasa = { viewModel.iniciarNavegacion() },
                                onBuscarCasa = { viewModel.cambiarPantalla(Pantalla.SEARCH) },
                                hasLocationPermission = hasLocationPermission()
                            )
                        }
                        Pantalla.SEARCH -> {
                            SearchScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.cambiarPantalla(Pantalla.MAP) },
                                onGuardarComoCasa = {
                                    uiState.lugarSeleccionado?.let { lugar ->
                                        viewModel.guardarCasaDesdeLugar(lugar)
                                    }
                                }
                            )
                        }
                        Pantalla.NAVEGACION -> {
                            NavigationScreen(
                                viewModel = viewModel,
                                onBack = { 
                                    viewModel.detenerNavegacion()
                                    viewModel.cambiarPantalla(Pantalla.MAP)
                                }
                            )
                        }
                        else -> {
                            MainScreen(
                                viewModel = viewModel,
                                onRequestPermission = { requestLocationPermission() },
                                onIrACasa = { viewModel.iniciarNavegacion() },
                                onBuscarCasa = { viewModel.cambiarPantalla(Pantalla.SEARCH) },
                                hasLocationPermission = hasLocationPermission()
                            )
                        }
                    }
                }
            }
        }

        if (hasLocationPermission()) {
            viewModel.obtenerUbicacionUnica()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.cargarCasa()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }
}