package com.example.regresoacasa

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.File
import androidx.appcompat.app.AlertDialog
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

    companion object {
        private const val TAG = "MainActivity"
    }

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
        try {
            Log.d(TAG, "onCreate iniciando")
            
            // Verificar si hay error previo guardado
            verificarErrorPrevio()
            
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
                                    onBuscarDestino = { 
                                        viewModel.onSearchQueryChange("")
                                        viewModel.cambiarPantalla(Pantalla.SEARCH) 
                                    },
                                    onBuscarCasa = { 
                                        viewModel.onSearchQueryChange("")
                                        viewModel.cambiarPantalla(Pantalla.SEARCH) 
                                    },
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
                                    },
                                    onIrADestino = {
                                        uiState.lugarSeleccionado?.let { lugar ->
                                            viewModel.iniciarNavegacionConDestino(lugar)
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
                                    onBuscarDestino = { viewModel.cambiarPantalla(Pantalla.SEARCH) },
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
            Log.d(TAG, "onCreate completado")
        } catch (e: Exception) {
            val errorMsg = "ERROR: ${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
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
    
    private fun verificarErrorPrevio() {
        try {
            val errorFile = File(filesDir, "crash_error.txt")
            if (errorFile.exists()) {
                val errorContent = errorFile.readText()
                // Mostrar diálogo con el error
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Error detectado")
                        .setMessage("El error fue:\n\n$errorContent\n\nCopia este texto y repórtalo.")
                        .setPositiveButton("Aceptar") { dialog, _ ->
                            // Copiar al portapapeles
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Error", errorContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cerrar") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setCancelable(false)
                        .show()
                }
                // Eliminar archivo después de mostrar
                errorFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando error previo", e)
        }
    }
}