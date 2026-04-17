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
import com.example.regresoacasa.domain.model.Lugar
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.ui.components.MapaView
import com.example.regresoacasa.ui.state.UiState
import com.example.regresoacasa.ui.viewmodel.NavigationViewModel

@Composable
fun MainScreen(
    viewModel: NavigationViewModel,
    onRequestPermission: () -> Unit,
    onIrACasa: () -> Unit,
    onBuscarDestino: () -> Unit,
    onBuscarCasa: () -> Unit,
    hasLocationPermission: Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsMenu by remember { mutableStateOf(false) }

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
                // Selector de Capas de Mapa
                MapStyleSelector(
                    currentStyle = uiState.mapStyle,
                    onStyleSelected = { viewModel.cambiarEstiloMapa(it) }
                )

                // Botón Guardian (Emergencia)
                FloatingActionButton(
                    onClick = { viewModel.sendEmergencyAlert() },
                    containerColor = if (uiState.isSafeReturnActive) Color.Red else Color.White,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = "Emergencia",
                        tint = if (uiState.isSafeReturnActive) Color.White else Color(0xFFD32F2F),
                        modifier = Modifier.size(24.dp)
                    )
                }

                FloatingButtons(
                    tieneCasa = uiState.casa != null,
                    hasLocationPermission = hasLocationPermission,
                    onRequestPermission = onRequestPermission,
                    onMiUbicacion = { viewModel.obtenerUbicacionUnica() },
                    onBuscarDestino = onBuscarDestino,
                    onBuscarCasa = onBuscarCasa,
                    onIrACasa = onIrACasa
                )
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
private fun MapStyleSelector(
    currentStyle: String,
    onStyleSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val styles = listOf("Normal", "Satélite", "Transporte", "Topográfico")

    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Card(
                modifier = Modifier.padding(bottom = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    styles.forEach { style ->
                        TextButton(
                            onClick = {
                                onStyleSelected(style)
                                expanded = false
                            },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = style,
                                color = if (currentStyle == style) Color(0xFF1565C0) else Color.DarkGray,
                                fontWeight = if (currentStyle == style) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = Color.White,
            modifier = Modifier.size(40.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = "Capas",
                tint = Color(0xFF1565C0),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FloatingButtons(
    tieneCasa: Boolean,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onMiUbicacion: () -> Unit,
    onBuscarDestino: () -> Unit,
    onBuscarCasa: () -> Unit,
    onIrACasa: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Botón Buscar Destino (Nuevo)
        FloatingActionButton(
            onClick = onBuscarDestino,
            containerColor = Color.White,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar destino",
                tint = Color(0xFF1565C0)
            )
        }

        // Botón mi ubicación
        FloatingActionButton(
            onClick = {
                if (hasLocationPermission) onMiUbicacion() else onRequestPermission()
            },
            containerColor = Color.White,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Mi ubicación",
                tint = Color(0xFF1565C0)
            )
        }

        // Botón principal: Ir a Casa o Configurar Casa
        if (tieneCasa) {
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
