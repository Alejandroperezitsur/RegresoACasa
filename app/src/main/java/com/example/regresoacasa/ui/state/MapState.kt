package com.example.regresoacasa.ui.state

/**
 * FASE 8: Estado del mapa persistente en rotación
 * Se guarda en SavedStateHandle para sobrevivir a recreaciones
 */
data class MapState(
    val centerLat: Double = 0.0,
    val centerLng: Double = 0.0,
    val zoom: Double = 15.0,
    val isFollowingUser: Boolean = true
) {
    companion object {
        const val KEY_CENTER_LAT = "map_center_lat"
        const val KEY_CENTER_LNG = "map_center_lng"
        const val KEY_ZOOM = "map_zoom"
        const val KEY_FOLLOWING = "map_following"
    }
}
