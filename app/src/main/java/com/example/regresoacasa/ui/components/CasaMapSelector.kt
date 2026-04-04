package com.example.regresoacasa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasaMapSelector(
    ubicacionInicial: GeoPoint? = null,
    onDismiss: () -> Unit,
    onGuardar: (lat: Double, lng: Double, direccion: String) -> Unit,
    estaCargando: Boolean,
    viewModel: com.example.regresoacasa.ui.viewmodel.MapViewModel
) {
    val context = LocalContext.current
    var mapView: MapView? by remember { mutableStateOf(null) }
    var centerPoint by remember { mutableStateOf(ubicacionInicial ?: GeoPoint(19.4326, -99.1332)) } // CDMX por defecto
    var direccion by remember { mutableStateOf<String?>(null) }
    var estaBuscandoDireccion by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Obtener dirección al mover el mapa
    LaunchedEffect(centerPoint) {
        if (estaBuscandoDireccion) return@LaunchedEffect
        estaBuscandoDireccion = true
        delay(500) // Debounce
        
        try {
            // Timeout de 5 segundos para la llamada
            val result = withTimeoutOrNull(5000) {
                viewModel.obtenerDireccionDeCoordenadas(centerPoint.latitude, centerPoint.longitude)
            }
            direccion = result ?: "${String.format("%.5f", centerPoint.latitude)}, ${String.format("%.5f", centerPoint.longitude)}"
        } catch (e: Exception) {
            direccion = "Ubicación seleccionada (${String.format("%.5f", centerPoint.latitude)}, ${String.format("%.5f", centerPoint.longitude)})"
        } finally {
            estaBuscandoDireccion = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Mapa
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    
                    ubicacionInicial?.let {
                        controller.setCenter(it)
                    } ?: run {
                        // Intentar centrar en ubicación actual si está disponible
                        controller.setCenter(centerPoint)
                    }
                    
                    // Listener para detectar cuando se detiene el scroll
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            event?.let {
                                centerPoint = projection.fromPixels(width / 2, height / 2) as GeoPoint
                            }
                            return true
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            event?.let {
                                centerPoint = projection.fromPixels(width / 2, height / 2) as GeoPoint
                            }
                            return true
                        }
                    })
                    
                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                // Actualizar marcador de referencia visual
                map.overlays.removeAll { it is Marker && it.id == "center" }
            }
        )

        // Marcador central (crosshair visual)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6F00)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                // Pin que apunta hacia abajo
                Box(
                    modifier = Modifier
                        .size(0.dp)
                        .background(Color.Transparent),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Triángulo simulado con spacer
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // Header superior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1565C0))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Selecciona tu Casa",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Mueve el mapa para ubicar tu casa",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White
                    )
                }
            }
        }

        // Botón de búsqueda (esquina superior derecha)
        FloatingActionButton(
            onClick = { showSearchDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 16.dp)
                .size(48.dp),
            containerColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar dirección",
                tint = Color(0xFF1565C0)
            )
        }

        // Botón de mi ubicación (esquina superior derecha, debajo)
        FloatingActionButton(
            onClick = {
                scope.launch {
                    viewModel.ubicacionActual.value?.let { ubicacion ->
                        centerPoint = ubicacion
                        mapView?.controller?.setCenter(ubicacion)
                        mapView?.controller?.setZoom(17.0)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 136.dp, end = 16.dp)
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

        // Card inferior con dirección y botón guardar
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // Indicador de dirección
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFF3E0)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (estaBuscandoDireccion || estaCargando) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFFFF6F00),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6F00),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dirección seleccionada:",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray
                            )
                            Text(
                                text = direccion ?: "Cargando dirección...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 2
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Coordenadas
                    Text(
                        text = "${String.format("%.5f", centerPoint.latitude)}, ${String.format("%.5f", centerPoint.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botón guardar
                    Button(
                        onClick = {
                            onGuardar(
                                centerPoint.latitude,
                                centerPoint.longitude,
                                direccion ?: "Casa"
                            )
                        },
                        enabled = !estaCargando && !estaBuscandoDireccion,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6F00),
                            disabledContainerColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (estaCargando) "Guardando..." else "✓ Establecer como Casa"
                        )
                    }
                }
            }
        }
    }

    // Diálogo de búsqueda
    if (showSearchDialog) {
        SearchAddressDialog(
            onDismiss = { showSearchDialog = false },
            onBuscar = { query ->
                scope.launch {
                    try {
                        val result = viewModel.buscarDireccion(query)
                        result?.let { (lat, lng, addr) ->
                            centerPoint = GeoPoint(lat, lng)
                            direccion = addr
                            mapView?.controller?.setCenter(centerPoint)
                            mapView?.controller?.setZoom(17.0)
                        }
                    } catch (e: Exception) {
                        // Error silencioso, el ViewModel ya maneja el error
                    }
                    showSearchDialog = false
                }
            },
            estaCargando = estaCargando
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAddressDialog(
    onDismiss: () -> Unit,
    onBuscar: (String) -> Unit,
    estaCargando: Boolean
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar Dirección") },
        text = {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Dirección") },
                placeholder = { Text("Ej: Calle Principal 123, Ciudad") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !estaCargando
            )
        },
        confirmButton = {
            Button(
                onClick = { onBuscar(query) },
                enabled = query.isNotBlank() && !estaCargando
            ) {
                if (estaCargando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Buscar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
