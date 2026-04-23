package com.example.regresoacasa.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mapStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "map_state")

class MapStateManager(private val context: Context) {
    
    private val ZOOM_KEY = floatPreferencesKey("map_zoom")
    private val CENTER_LAT_KEY = doublePreferencesKey("map_center_lat")
    private val CENTER_LON_KEY = doublePreferencesKey("map_center_lon")
    
    val mapState: Flow<MapState> = context.mapStateDataStore.data.map { preferences ->
        MapState(
            zoom = preferences[ZOOM_KEY] ?: 15f,
            centerLat = preferences[CENTER_LAT_KEY] ?: 19.4326,
            centerLon = preferences[CENTER_LON_KEY] ?: -99.1332
        )
    }
    
    suspend fun saveMapState(zoom: Float, centerLat: Double, centerLon: Double) {
        context.mapStateDataStore.edit { preferences ->
            preferences[ZOOM_KEY] = zoom
            preferences[CENTER_LAT_KEY] = centerLat
            preferences[CENTER_LON_KEY] = centerLon
        }
    }
    
    suspend fun clear() {
        context.mapStateDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

data class MapState(
    val zoom: Float,
    val centerLat: Double,
    val centerLon: Double
)
