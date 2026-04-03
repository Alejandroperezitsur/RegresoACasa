package com.example.regresoacasa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.regresoacasa.ui.components.CasaConfigDialog
import com.example.regresoacasa.ui.components.OsmMap
import com.example.regresoacasa.ui.components.RouteInfoCard
import com.example.regresoacasa.ui.viewmodel.MapViewModel
import org.osmdroid.views.MapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MapViewModel,
    onRequestLocationPermission: () -> Unit
) {
    val ubicacionActual by viewModel.ubicacionActual.collectAsStateWithLifecycle()
    val casaUbicacion by viewModel.casaUbicacion.collectAsStateWithLifecycle()
    val casaDireccion by viewModel.casaDireccion.collectAsStateWithLifecycle()
    val rutaPolilinea by viewModel.rutaPolilinea.collectAsStateWithLifecycle()
    val rutaInfo by viewModel.rutaInfo.collectAsStateWithLifecycle()
    val estaCargando by viewModel.estaCargando.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val apiKeyConfigurada by viewModel.apiKeyConfigurada.collectAsStateWithLifecycle()

    var mostrarConfigDialog by remember { mutableStateOf(false) }
    var mapView: MapView? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Regreso a Casa") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { mostrarConfigDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurar casa"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        if (ubicacionActual == null) {
                            viewModel.obtenerUbicacionActual()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Mi ubicación"
                    )
                }

                FloatingActionButton(
                    onClick = {
                        if (casaUbicacion == null) {
                            mostrarConfigDialog = true
                        } else {
                            viewModel.calcularRutaATipo("foot-walking")
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Ruta a casa"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OsmMap(
                ubicacionActual = ubicacionActual,
                casaUbicacion = casaUbicacion,
                rutaPolilinea = rutaPolilinea,
                onMapReady = { mapView = it }
            )

            if (estaCargando) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            error?.let { errorMsg ->
                AlertDialog(
                    onDismissRequest = { viewModel.limpiarError() },
                    title = { Text("Error") },
                    text = { Text(errorMsg) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.limpiarError() }) {
                            Text("Aceptar")
                        }
                    }
                )
            }

            rutaInfo?.let { route ->
                RouteInfoCard(
                    route = route,
                    distanciaFormateada = viewModel.formatearDistancia(route.summary.distance),
                    duracionFormateada = viewModel.formatearDuracion(route.summary.duration),
                    onCerrar = { viewModel.limpiarRuta() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            if (ubicacionActual == null && !estaCargando) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Obtén tu ubicación para comenzar",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.obtenerUbicacionActual() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Obtener Ubicación")
                        }
                    }
                }
            }

            if (casaUbicacion == null && ubicacionActual != null && !mostrarConfigDialog) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Configura tu dirección de casa",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { mostrarConfigDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configurar Casa")
                        }
                    }
                }
            }
        }
    }

    if (mostrarConfigDialog) {
        CasaConfigDialog(
            direccionActual = casaDireccion,
            onDismiss = { mostrarConfigDialog = false },
            onGuardar = { direccion, apiKey ->
                viewModel.buscarYGuardarCasa(direccion, apiKey)
                mostrarConfigDialog = false
            },
            estaCargando = estaCargando
        )
    }
}
