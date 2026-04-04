package com.example.regresoacasa.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay

@Composable
fun MapaView(
    ubicacion: UbicacionUsuario?,
    destino: LugarFavorito?,
    ruta: List<PuntoRuta>?,
    modifier: Modifier = Modifier,
    isFollowingUser: Boolean = true,
    onFollowUserToggle: () -> Unit = {}
) {
    val context = LocalContext.current

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var markerUsuario by remember { mutableStateOf<Marker?>(null) }
    var markerDestino by remember { mutableStateOf<Marker?>(null) }
    var polylineRuta by remember { mutableStateOf<Polyline?>(null) }

    DisposableEffect(context) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
        onDispose {
            mapViewInstance?.onPause()
        }
    }

    // Auto-center when following user
    LaunchedEffect(ubicacion, isFollowingUser) {
        if (isFollowingUser && ubicacion != null && mapViewInstance != null) {
            val geoPoint = GeoPoint(ubicacion.latitud, ubicacion.longitud)
            mapViewInstance?.controller?.animateTo(geoPoint)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)

                    val compass = CompassOverlay(ctx, this)
                    compass.enableCompass()
                    overlays.add(compass)

                    mapViewInstance = this
                }
            },
            update = { mapView ->
                // Actualizar marcador de usuario
                markerUsuario?.let { mapView.overlays.remove(it) }
                ubicacion?.let { loc ->
                    val geoPoint = GeoPoint(loc.latitud, loc.longitud)
                    val marker = Marker(mapView).apply {
                        position = geoPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Tu ubicación"
                    }
                    mapView.overlays.add(marker)
                    markerUsuario = marker
                }

                // Actualizar marcador de destino
                markerDestino?.let { mapView.overlays.remove(it) }
                destino?.let { dest ->
                    val geoPoint = GeoPoint(dest.latitud, dest.longitud)
                    val marker = crearMarcadorDestino(mapView, geoPoint, dest.nombre)
                    mapView.overlays.add(marker)
                    markerDestino = marker
                }

                // Actualizar polilínea de ruta
                polylineRuta?.let { mapView.overlays.remove(it) }
                ruta?.let { puntos ->
                    if (puntos.isNotEmpty()) {
                        val polyline = Polyline().apply {
                            setPoints(puntos.map { GeoPoint(it.latitud, it.longitud) })
                            outlinePaint.strokeWidth = 12f
                            outlinePaint.color = Color.parseColor("#1565C0")
                        }
                        mapView.overlays.add(polyline)
                        polylineRuta = polyline
                    }
                }

                mapView.invalidate()
            }
        )

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
