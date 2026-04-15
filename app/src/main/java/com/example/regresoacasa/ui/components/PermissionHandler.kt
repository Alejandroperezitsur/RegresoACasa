package com.example.regresoacasa.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationPermissionHandler(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit = {},
    content: @Composable (isGranted: Boolean) -> Unit
) {
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    ) { results ->
        // Callback cuando el usuario responde al diálogo de permisos
        val allGranted = results.values.all { it }
        if (allGranted) {
            onPermissionGranted()
        } else {
            // Verificar si el usuario seleccionó "No volver a preguntar"
            val permanentlyDenied = locationPermissionsState.permissions.any { 
                !it.status.isGranted && !it.status.shouldShowRationale
            }
            if (permanentlyDenied) {
                showSettingsDialog = true
            }
            onPermissionDenied()
        }
    }
    
    // Verificar permisos al iniciar y cuando el lifecycle cambia
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Re-verificar permisos al volver de Settings
                if (locationPermissionsState.allPermissionsGranted) {
                    onPermissionGranted()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Diálogo para permisos permanentemente denegados
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Permiso requerido") },
            text = {
                Column {
                    Text(
                        "La aplicación necesita acceso a tu ubicación para calcular rutas y mostrar tu posición en el mapa.",
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Has bloqueado el permiso permanentemente. Por favor, abre la configuración de la aplicación para habilitarlo.",
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialog = false
                        // Abrir Settings de la app
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Abrir Configuración")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Contenido principal
    val isGranted = locationPermissionsState.allPermissionsGranted
    content(isGranted)
    
    // Solicitar permisos automáticamente si no están concedidos
    if (!isGranted && !showSettingsDialog) {
        DisposableEffect(Unit) {
            locationPermissionsState.launchMultiplePermissionRequest()
            onDispose { }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberLocationPermissionState(
    onPermissionGranted: () -> Unit = {},
    onPermissionDenied: () -> Unit = {}
): PermissionStateWrapper {
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            onPermissionGranted()
        } else {
            val permanentlyDenied = permissionState.permissions.any { 
                !it.status.isGranted && !it.status.shouldShowRationale
            }
            if (permanentlyDenied) {
                showSettingsDialog = true
            }
            onPermissionDenied()
        }
    }
    
    // Diálogo de Settings
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Permiso de ubicación requerido") },
            text = {
                Text(
                    "Para usar la navegación, necesitas habilitar el permiso de ubicación en la configuración de la aplicación.",
                    textAlign = TextAlign.Start
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Ir a Configuración")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    return PermissionStateWrapper(
        isGranted = permissionState.allPermissionsGranted,
        shouldShowRationale = permissionState.shouldShowRationale,
        launchPermissionRequest = { permissionState.launchMultiplePermissionRequest() }
    )
}

data class PermissionStateWrapper(
    val isGranted: Boolean,
    val shouldShowRationale: Boolean,
    val launchPermissionRequest: () -> Unit
)
