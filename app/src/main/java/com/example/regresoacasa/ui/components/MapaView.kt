package com.example.regresoacasa.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.PuntoRuta
import com.example.regresoacasa.domain.model.UbicacionUsuario
import android.util.Log
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay

private const val TAG = "MapaView"

@Composable
fun MapaView(
    ubicacion: UbicacionUsuario?,
    destino: LugarFavorito?,
    ruta: List<PuntoRuta>?,
    modifier: Modifier = Modifier,
    isFollowingUser: Boolean = true,
    isSelectingOnMap: Boolean = false,
    onFollowUserToggle: () -> Unit = {},
    onMapMove: (Double, Double) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    var mapError by remember { mutableStateOf<String?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var markerUsuario by remember { mutableStateOf<Marker?>(null) }
    var markerDestino by remember { mutableStateOf<Marker?>(null) }
    var polylineRuta by remember { mutableStateOf<Polyline?>(null) }

    val mapListener = remember {
        object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                event?.source?.let { map ->
                    val center = map.mapCenter
                    onMapMove(center.latitude, center.longitude)
                }
                return true
            }

            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                event?.source?.let { map ->
                    val center = map.mapCenter
                    onMapMove(center.latitude, center.longitude)
                }
                return true
            }
        }
    }

    DisposableEffect(context) {
        try {
            Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
            Configuration.getInstance().userAgentValue = context.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando OSMDroid", e)
        }
        onDispose {
            try {
                mapViewInstance?.onPause()
            } catch (e: Exception) {
                Log.e(TAG, "Error en onPause", e)
            }
        }
    }

    // Auto-center when following user (NOT when selecting on map)
    LaunchedEffect(ubicacion, isFollowingUser, isSelectingOnMap) {
        if (isFollowingUser && !isSelectingOnMap && ubicacion != null && mapViewInstance != null) {
            val geoPoint = GeoPoint(ubicacion.latitud, ubicacion.longitud)
            mapViewInstance?.controller?.animateTo(geoPoint)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        addMapListener(mapListener)

                        try {
                            val compass = CompassOverlay(ctx, this)
                            compass.enableCompass()
                            overlays.add(compass)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error creando brújula", e)
                        }

                        mapViewInstance = this
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creando MapView", e)
                    mapError = e.message
                    MapView(ctx)
                }
            },
            update = { mapView ->
                try {
                    // Actualizar marcador de usuario
                    markerUsuario?.let {
                        try { mapView.overlays.remove(it) } catch (e: Exception) { }
                    }
                    ubicacion?.let { loc ->
                        try {
                            val geoPoint = GeoPoint(loc.latitud, loc.longitud)
                            val marker = Marker(mapView).apply {
                                position = geoPoint
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Tu ubicación"
                            }
                            mapView.overlays.add(marker)
                            markerUsuario = marker
                        } catch (e: Exception) {
                            Log.w(TAG, "Error creando marcador usuario", e)
                        }
                    }

                    // Actualizar marcador de destino
                    markerDestino?.let {
                        try { mapView.overlays.remove(it) } catch (e: Exception) { }
                    }
                    destino?.let { dest ->
                        try {
                            val geoPoint = GeoPoint(dest.latitud, dest.longitud)
                            val marker = crearMarcadorDestino(mapView, geoPoint, dest.nombre)
                            mapView.overlays.add(marker)
                            markerDestino = marker
                        } catch (e: Exception) {
                            Log.w(TAG, "Error creando marcador destino", e)
                        }
                    }

                    // Actualizar polilínea de ruta
                    polylineRuta?.let {
                        try { mapView.overlays.remove(it) } catch (e: Exception) { }
                    }
                    ruta?.let { puntos ->
                        try {
                            if (puntos.isNotEmpty()) {
                                Log.d(TAG, "Dibujando polilínea con ${puntos.size} puntos")
                                val polyline = Polyline(mapView).apply {
                                    setPoints(puntos.map { GeoPoint(it.latitud, it.longitud) })
                                    outlinePaint.strokeWidth = 14f
                                    outlinePaint.color = Color.parseColor("#1565C0")
                                    // Añadir un borde blanco para mejor visibilidad
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                }
                                mapView.overlays.add(polyline)
                                polylineRuta = polyline
                                
                                // Si es la primera vez que se carga la ruta, hacer zoom para que quepa toda
                                if (!isFollowingUser && puntos.isNotEmpty()) {
                                    try {
                                        mapView.zoomToBoundingBox(polyline.bounds, true, 100)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error haciendo zoom a la ruta", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error creando ruta", e)
                        }
                    }

                    mapView.invalidate()
                } catch (e: Exception) {
                    Log.e(TAG, "Error actualizando mapa", e)
                }
            }
        )

        // Fallback UI cuando el mapa falla
        if (mapError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Cargando mapa...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Botón para recentrar (visible cuando no se está siguiendo al usuario)
        if (!isFollowingUser) {
            FloatingActionButton(
                onClick = onFollowUserToggle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(140.dp, 16.dp, 16.dp, 0.dp)
                    .padding(top = 80.dp),
                containerColor = androidx.compose.ui.graphics.Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Recentrar",
                    tint = androidx.compose.ui.graphics.Color(0xFF1565C0)
                )
            }
        }
    }
}

private fun crearMarcadorDestino(mapView: MapView, position: GeoPoint, titulo: String): Marker {
    val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = Color.parseColor("#E53935")
        isAntiAlias = true
    }
    canvas.drawCircle(24f, 24f, 20f, paint)

    return Marker(mapView).apply {
        this.position = position
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        this.title = titulo
        snippet = "Destino"
        icon = BitmapDrawable(mapView.resources, bitmap)
    }
}
