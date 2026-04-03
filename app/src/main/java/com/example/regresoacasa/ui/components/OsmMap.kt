package com.example.regresoacasa.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun OsmMap(
    ubicacionActual: GeoPoint?,
    casaUbicacion: GeoPoint?,
    rutaPolilinea: Polyline?,
    modifier: Modifier = Modifier,
    onMapReady: (MapView) -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))

                val mapView = MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                }

                val compassOverlay = CompassOverlay(context, mapView)
                compassOverlay.enableCompass()
                mapView.overlays.add(compassOverlay)

                onMapReady(mapView)
                mapView
            },
            update = { mapView ->
                mapView.overlays.clear()

                ubicacionActual?.let { location ->
                    val locationOverlay = MyLocationNewOverlay(mapView)
                    locationOverlay.enableMyLocation()
                    mapView.overlays.add(locationOverlay)

                    val marker = Marker(mapView).apply {
                        position = location
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Tu ubicación"
                    }
                    mapView.overlays.add(marker)

                    mapView.controller.animateTo(location)
                }

                casaUbicacion?.let { casa ->
                    val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    val paint = Paint().apply {
                        color = Color.RED
                        isAntiAlias = true
                    }
                    canvas.drawCircle(24f, 24f, 20f, paint)

                    val marker = Marker(mapView).apply {
                        position = casa
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Casa"
                        snippet = "Tu destino"
                        icon = BitmapDrawable(mapView.resources, bitmap)
                    }
                    mapView.overlays.add(marker)
                }

                rutaPolilinea?.let { polyline ->
                    mapView.overlays.add(polyline)
                }

                mapView.invalidate()
            }
        )
    }
}
