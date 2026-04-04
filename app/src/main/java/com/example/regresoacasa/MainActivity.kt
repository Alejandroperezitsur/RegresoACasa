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
import com.example.regresoacasa.ui.viewmodel.MainViewModel
import com.example.regresoacasa.ui.viewmodel.Pantalla

class MainActivity : ComponentActivity() {

    private val appModule by lazy { AppModule.getInstance(this) }
    
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(appModule)
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                viewModel.obtenerUbicacion()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                viewModel.obtenerUbicacion()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
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
                                onIrACasa = { viewModel.irACasa() },
                                onBuscarCasa = { viewModel.cambiarPantalla(Pantalla.SEARCH) },
                                onAbrirAjustes = { },
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
                                    viewModel.limpiarRuta()
                                    viewModel.cambiarPantalla(Pantalla.MAP)
                                }
                            )
                        }
                        else -> {
                            MainScreen(
                                viewModel = viewModel,
                                onRequestPermission = { requestLocationPermission() },
                                onIrACasa = { viewModel.irACasa() },
                                onBuscarCasa = { viewModel.cambiarPantalla(Pantalla.SEARCH) },
                                onAbrirAjustes = { },
                                hasLocationPermission = hasLocationPermission()
                            )
                        }
                    }
                }
            }
        }

        if (hasLocationPermission()) {
            viewModel.obtenerUbicacion()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) {
            viewModel.cargarCasa()
        }
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