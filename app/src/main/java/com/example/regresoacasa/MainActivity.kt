package com.example.regresoacasa

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
        val fineLocation = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
        val coarseLocation = permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        
        when {
            fineLocation || coarseLocation -> {
                viewModel.obtenerUbicacionUnica()
                // FASE 2: Solicitar permisos de background location después de foreground
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    requestBackgroundLocationPermission()
                }
            }
            else -> {
                // Permiso denegado - verificar si es permanente
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    showPermissionRationaleDialog()
                } else {
                    showPermissionDeniedDialog()
                }
            }
        }
    }

    private val backgroundLocationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Permiso de ubicación en background concedido", Toast.LENGTH_SHORT).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    showBackgroundPermissionDeniedDialog()
                }
            }
        }
    }

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Permiso de notificaciones concedido", Toast.LENGTH_SHORT).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    showNotificationPermissionDeniedDialog()
                }
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
                                    onRequestSmsPermission = { requestSmsPermission() },
                                    onIrACasa = { viewModel.iniciarNavegacion() },
                                    onBuscarDestino = { 
                                        viewModel.onSearchQueryChange("")
                                        viewModel.cambiarPantalla(Pantalla.SEARCH) 
                                    },
                                    onBuscarCasa = { 
                                        viewModel.onSearchQueryChange("")
                                        viewModel.cambiarPantalla(Pantalla.SEARCH) 
                                    },
                                    hasLocationPermission = hasLocationPermission(),
                                    hasSmsPermission = hasSmsPermission()
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
                                    onRequestSmsPermission = { requestSmsPermission() },
                                    onIrACasa = { viewModel.iniciarNavegacion() },
                                    onBuscarDestino = { viewModel.cambiarPantalla(Pantalla.SEARCH) },
                                    onBuscarCasa = { viewModel.cambiarPantalla(Pantalla.SEARCH) },
                                    hasLocationPermission = hasLocationPermission(),
                                    hasSmsPermission = hasSmsPermission()
                                )
                            }
                        }
                    }
                }
            }

            if (hasLocationPermission()) {
                viewModel.obtenerUbicacionUnica()
            }
            // FASE 2: Solicitar permiso de notificaciones en Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission()
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

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        // FASE 2: Mostrar explicación antes de pedir permisos
        showLocationPermissionDialog()
    }

    private fun showLocationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de ubicación requerido")
            .setMessage(
                "Regreso a Casa necesita tu ubicación para:\n\n" +
                "• Calcular rutas a tu destino\n" +
                "• Navegación en tiempo real\n" +
                "• Función Guardian (alertas de emergencia)\n\n" +
                "Tu ubicación se usa solo para navegación y nunca se comparte."
            )
            .setPositiveButton("Conceder") { _, _ ->
                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Ubicación en background")
                    .setMessage(
                        "Para que la navegación funcione cuando la app está en segundo plano, " +
                        "necesitamos permiso de ubicación en background.\n\n" +
                        "Esto permite que la app te siga incluso si minimizas la pantalla."
                    )
                    .setPositiveButton("Permitir siempre") { _, _ ->
                        backgroundLocationPermissionRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("Solo mientras uso la app") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso necesario")
            .setMessage("Regreso a Casa no puede funcionar sin acceso a tu ubicación. Por favor, concede el permiso.")
            .setPositiveButton("Intentar de nuevo") { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso denegado permanentemente")
            .setMessage("Has denegado el permiso de ubicación. Ve a Configuración de la app para habilitarlo manualmente.")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showBackgroundPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Ubicación en background denegada")
            .setMessage("La navegación en background no funcionará. Ve a Configuración para habilitar "ubicación siempre".")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showNotificationPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notificaciones denegadas")
            .setMessage("No recibirás alertas de navegación ni notificaciones del Guardian. Ve a Configuración para habilitarlas.")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo configuración", e)
        }
    }

    private fun requestSmsPermission() {
        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.SEND_SMS
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