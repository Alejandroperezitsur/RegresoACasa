package com.example.regresoacasa.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.regresoacasa.ui.components.CasaConfigDialog
import com.example.regresoacasa.ui.components.OsmMap
import com.example.regresoacasa.ui.components.RouteInfoCard
import com.example.regresoacasa.ui.viewmodel.MapViewModel
import org.osmdroid.views.MapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onRequestLocationPermission: () -> Unit,
    onOpenGpsSettings: () -> Unit,
    isGpsEnabled: () -> Boolean,
    hasLocationPermission: () -> Boolean
) {
    val ubicacionActual by viewModel.ubicacionActual.collectAsStateWithLifecycle()
    val casaUbicacion by viewModel.casaUbicacion.collectAsStateWithLifecycle()
    val casaDireccion by viewModel.casaDireccion.collectAsStateWithLifecycle()
    val rutaPolilinea by viewModel.rutaPolilinea.collectAsStateWithLifecycle()
    val rutaInfo by viewModel.rutaInfo.collectAsStateWithLifecycle()
    val estaCargando by viewModel.estaCargando.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var mostrarConfigDialog by remember { mutableStateOf(false) }
    var mostrarInfoRuta by remember { mutableStateOf(false) }
    var mapView: MapView? by remember { mutableStateOf(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Mapa a pantalla completa
        OsmMap(
            ubicacionActual = ubicacionActual,
            casaUbicacion = casaUbicacion,
            rutaPolilinea = rutaPolilinea,
            onMapReady = { mapView = it }
        )

        // Overlay superior con gradiente
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1565C0).copy(alpha = 0.9f),
                            Color(0xFF1565C0).copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Regreso a Casa",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                if (casaDireccion != null) {
                    Text(
                        text = "Casa: $casaDireccion",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.9f)),
                        maxLines = 1
                    )
                }
            }
        }

        // Botón de ubicación (arriba a la derecha)
        FloatingActionButton(
            onClick = {
                if (!hasLocationPermission()) {
                    onRequestLocationPermission()
                } else if (!isGpsEnabled()) {
                    onOpenGpsSettings()
                } else {
                    viewModel.obtenerUbicacionActual()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 140.dp, end = 16.dp)
                .size(48.dp),
            containerColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Mi ubicación",
                tint = Color(0xFF1565C0)
            )
        }

        // Botón de casa (arriba a la derecha, debajo del de ubicación)
        FloatingActionButton(
            onClick = { mostrarConfigDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 196.dp, end = 16.dp)
                .size(48.dp),
            containerColor = if (casaUbicacion != null) Color(0xFF4CAF50) else Color(0xFFFF6F00),
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Configurar casa",
                tint = Color.White
            )
        }

        // Botón de ruta (abajo a la derecha)
        AnimatedVisibility(
            visible = ubicacionActual != null && casaUbicacion != null,
            enter = scaleIn(animationSpec = spring(Spring.DampingRatioMediumBouncy)),
            exit = scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            FloatingActionButton(
                onClick = {
                    viewModel.calcularRutaATipo("foot-walking")
                    mostrarInfoRuta = true
                },
                modifier = Modifier
                    .padding(bottom = 140.dp, end = 16.dp)
                    .size(56.dp),
                containerColor = Color(0xFF1565C0),
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Trazar ruta",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Indicador de carga centrado
        if (estaCargando) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
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
                        CircularProgressIndicator(
                            color = Color(0xFF1565C0),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Cargando...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF1565C0)
                        )
                    }
                }
            }
        }

        // Mensaje de error
        error?.let { errorMsg ->
            AlertDialog(
                onDismissRequest = { viewModel.limpiarError() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE53935)
                    )
                },
                title = { Text("Atención") },
                text = { Text(errorMsg) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.limpiarError() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) {
                        Text("Aceptar")
                    }
                }
            )
        }

        // Info de ruta (bottom sheet style)
        AnimatedVisibility(
            visible = rutaInfo != null && mostrarInfoRuta,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            rutaInfo?.let { route ->
                RouteInfoCard(
                    route = route,
                    distanciaFormateada = viewModel.formatearDistancia(route.summary.distance),
                    duracionFormateada = viewModel.formatearDuracion(route.summary.duration),
                    onCerrar = {
                        mostrarInfoRuta = false
                        viewModel.limpiarRuta()
                    }
                )
            }
        }

        // Estado vacío: sin ubicación
        if (ubicacionActual == null && !estaCargando && error == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(32.dp)
                        .shadow(8.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE3F2FD)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFF1565C0),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "¿Dónde estás?",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Necesitamos tu ubicación para calcular la ruta a casa",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (!hasLocationPermission()) {
                                    onRequestLocationPermission()
                                } else if (!isGpsEnabled()) {
                                    onOpenGpsSettings()
                                } else {
                                    viewModel.obtenerUbicacionActual()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Obtener mi ubicación")
                        }
                    }
                }
            }
        }

        // Estado: Ubicación OK pero sin casa configurada
        if (ubicacionActual != null && casaUbicacion == null && !estaCargando && error == null) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it / 2 }
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFF3E0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6F00),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "¿Dónde vives?",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = "Configura tu dirección de casa",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { mostrarConfigDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Configurar Casa")
                        }
                    }
                }
            }
        }
    }

    // Diálogo de configuración
    if (mostrarConfigDialog) {
        CasaConfigDialog(
            direccionActual = casaDireccion,
            onDismiss = { mostrarConfigDialog = false },
            onGuardar = { direccion ->
                viewModel.buscarYGuardarCasa(direccion)
                mostrarConfigDialog = false
            },
            estaCargando = estaCargando
        )
    }
}
