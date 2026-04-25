package com.example.regresoacasa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.ui.components.MapaView
import com.example.regresoacasa.ui.components.ReliabilityIndicator
import com.example.regresoacasa.ui.components.SafetyModeBanner
import com.example.regresoacasa.ui.components.SpeedDial
import com.example.regresoacasa.ui.components.SpeedDialItem
import com.example.regresoacasa.core.safety.state.SafetyMode
import com.example.regresoacasa.ui.state.UiState
import com.example.regresoacasa.ui.viewmodel.NavigationViewModel
import com.example.regresoacasa.ui.viewmodel.EmergencyViewModel
import com.example.regresoacasa.ui.viewmodel.SafetyStatusViewModel

@Composable
fun MainScreen(
    viewModel: NavigationViewModel,
    emergencyViewModel: EmergencyViewModel,
    safetyStatusViewModel: SafetyStatusViewModel,
    onRequestPermission: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onIrACasa: () -> Unit,
    onBuscarDestino: () -> Unit,
    onBuscarCasa: () -> Unit,
    hasLocationPermission: Boolean,
    hasSmsPermission: Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    val emergencyUiState by emergencyViewModel.uiState.collectAsState()
    val safetyUiState by safetyStatusViewModel.uiState.collectAsState()
    val safetyMode by safetyStatusViewModel.safetyMode.collectAsState()
    val safetyScore by safetyStatusViewModel.safetyScore.collectAsState()
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showGuardianDialog by remember { mutableStateOf(false) }
    var showEmergencyConfirm by remember { mutableStateOf(false) }

    if (showGuardianDialog) {
        var phoneNumber by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGuardianDialog = false },
            title = { Text("Configurar Guardian") },
            text = {
                Column {
                    Text("Introduce el número de teléfono de tu contacto de confianza para recibir alertas por SMS.")
                    androidx.compose.material3.OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Teléfono") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (phoneNumber.isNotBlank()) {
                            viewModel.toggleGuardian(phoneNumber)
                            showGuardianDialog = false
                        }
                    }
                ) {
                    Text("Activar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuardianDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showSettingsMenu) {
        AlertDialog(
            onDismissRequest = { showSettingsMenu = false },
            title = { Text("Configuración") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Opciones de tu ubicación guardada:")
                    Button(
                        onClick = {
                            viewModel.eliminarCasa()
                            showSettingsMenu = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar ubicación de casa")
                    }
                    
                    Text(
                        "Versión 1.0.0 - Regreso a Casa Seguro",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsMenu = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Mapa
        MapaView(
            ubicacion = uiState.ubicacionActual,
            destino = uiState.casa,
            ruta = uiState.rutaActual?.puntos,
            isFollowingUser = uiState.navigationState.isFollowingUser,
            isSelectingOnMap = uiState.isSelectingOnMap,
            onFollowUserToggle = { viewModel.toggleFollowUser() },
            onMapMove = { lat, lon -> viewModel.onMapMove(lat, lon) },
            onLongPress = { lat, lon -> 
                viewModel.onMapLongClick(lat, lon)
            },
            seleccion = uiState.mapCenterUbicacion,
            mapStyle = uiState.mapStyle
        )

        // Mira/Cursor central cuando se selecciona en mapa
        if (uiState.isSelectingOnMap) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 56.dp), // Ajuste por si hay UI inferior
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Header superior
        if (!uiState.isSelectingOnMap) {
            HeaderSection(
                casa = uiState.casa,
                onSettingsClick = { showSettingsMenu = true },
                modifier = Modifier.align(Alignment.TopCenter)
            )
            
            // V3: SafetyModeBanner - muestra modo real del sistema
            SafetyModeBanner(
                mode = safetyMode,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 130.dp)
            )
            
            // V3: ReliabilityIndicator - muestra score de seguridad
            ReliabilityIndicator(
                reliabilityScore = safetyScore,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 170.dp)
            )
        } else {
            // Header de selección
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1565C0))
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text(
                    text = "Mueve el mapa para elegir",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Botones flotantes o controles de selección
        if (!uiState.isSelectingOnMap) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botón de emergencia SIEMPRE visible - REQUIERE CONFIRMACIÓN
                FloatingActionButton(
                    onClick = { showEmergencyConfirm = true },
                    containerColor = Color.Red,
                    contentColor = Color.White,
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(8.dp, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alerta de emergencia",
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Emergency status indicator
                if (emergencyUiState.isActive) {
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(0.9f),
                        colors = CardDefaults.cardColors(
                            containerColor = when (emergencyUiState.deliveryStatus) {
                                is com.example.regresoacasa.core.EmergencyDeliveryStatus.DeliveredInternet,
                                is com.example.regresoacasa.core.EmergencyDeliveryStatus.DeliveredSMS -> Color(0xFF4CAF50)
                                is com.example.regresoacasa.core.EmergencyDeliveryStatus.FailedRetrying -> Color(0xFFFF9800)
                                is com.example.regresoacasa.core.EmergencyDeliveryStatus.PermanentlyFailed -> Color.Red
                                else -> Color(0xFF2196F3)
                            }
                        )
                    ) {
                        Text(
                            text = emergencyUiState.deliveryMessage ?: "Enviando...",
                            color = Color.White,
                            modifier = Modifier.padding(12.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                // Safety status indicator
                if (safetyUiState.gpsMessage != null || safetyUiState.connectionMessage != null) {
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(0.9f),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF9800)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            safetyUiState.gpsMessage?.let {
                                Text(it, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                            safetyUiState.connectionMessage?.let {
                                Text(it, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                // FASE 4: Speed Dial con máximo 3 FABs visibles
                val speedDialItems = buildList {
                    add(SpeedDialItem(
                        icon = Icons.Default.Layers,
                        label = "Capas"
                    ) {
                        showSettingsMenu = true
                    })
                    add(SpeedDialItem(
                        icon = Icons.Default.Search,
                        label = "Buscar"
                    ) {
                        onBuscarDestino()
                    })
                    add(SpeedDialItem(
                        icon = Icons.Default.LocationOn,
                        label = "Mi ubicación"
                    ) {
                        if (hasLocationPermission) {
                            viewModel.obtenerUbicacionUnica()
                        } else {
                            onRequestPermission()
                        }
                    })
                    if (uiState.isSafeReturnActive) {
                        add(SpeedDialItem(
                            icon = Icons.Default.Warning,
                            label = "Alerta"
                        ) {
                            viewModel.sendEmergencyAlert()
                        })
                    }
                }

                SpeedDial(
                    items = speedDialItems,
                    mainFabColor = if (uiState.isSafeReturnActive) Color.Red else Color(0xFF1565C0)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Botón principal: Ir a Casa o Configurar Casa
                if (uiState.casa != null) {
                    Button(
                        onClick = onIrACasa,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1565C0)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .shadow(8.dp, RoundedCornerShape(28.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Ir a Casa",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                } else {
                    Button(
                        onClick = onBuscarCasa,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6F00)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .shadow(8.dp, RoundedCornerShape(28.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Configurar Casa",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                        )
                        )
                    }
                }
            }
        } else {
            SelectionControls(
                onConfirmar = { viewModel.confirmarSeleccionMapa() },
                onCancelar = { viewModel.cancelarSeleccionMapa() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Indicadores de carga
        if (uiState.estaCargandoUbicacion || uiState.estaCalculandoRuta) {
            LoadingOverlay(
                mensaje = when {
                    uiState.estaCalculandoRuta -> "Calculando ruta..."
                    else -> "Obteniendo ubicación..."
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Mensaje de error
        if (uiState.uiState is UiState.Error) {
            val error = uiState.uiState as UiState.Error
            ErrorCard(
                mensaje = error.message,
                onDismiss = { 
                    if (error.message.contains("Permiso", ignoreCase = true)) {
                        onRequestPermission()
                    }
                    viewModel.limpiarError()
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Diálogo de confirmación de emergencia
        if (showEmergencyConfirm) {
            AlertDialog(
                onDismissRequest = { showEmergencyConfirm = false },
                title = { Text("⚠️ Confirmar Emergencia") },
                text = { 
                    Text("¿Estás seguro de enviar alerta de emergencia a tus contactos? Esto enviará SMS inmediatamente.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEmergencyConfirm = false
                            emergencyViewModel.triggerEmergency("Emergencia manual")
                        }
                    ) {
                        Text("CONFIRMAR", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmergencyConfirm = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
private fun HeaderSection(
    casa: LugarFavorito?,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1565C0).copy(alpha = 0.95f),
                        Color(0xFF1565C0).copy(alpha = 0.7f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Regreso a Casa",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                if (casa != null) {
                    Text(
                        text = casa.direccion,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "Configura tu casa para empezar",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configurar",
                    tint = Color.White
                )
            }
        }
    }
}


@Composable
private fun SelectionControls(
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Button(
            onClick = onCancelar,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
        ) {
            Text("Cancelar", color = Color.Black)
        }
        
        Button(
            onClick = onConfirmar,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
        ) {
            Text("Confirmar", color = Color.White)
        }
    }
}

@Composable
private fun LoadingOverlay(
    mensaje: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color(0xFF1565C0))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = mensaje,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    mensaje: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(32.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️ Atención",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC62828)
                )
            ) {
                Text("Entendido")
            }
        }
    }
}
