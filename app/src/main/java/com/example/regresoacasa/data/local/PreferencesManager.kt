package com.example.regresoacasa.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "regreso_a_casa_prefs"
        private const val KEY_CASA_LAT = "casa_lat"
        private const val KEY_CASA_LNG = "casa_lng"
        private const val KEY_CASA_DIRECCION = "casa_direccion"
        private const val KEY_ORS_API_KEY = "ors_api_key"
        private const val KEY_ONBOARDING_VISTO = "onboarding_visto"
        
        // API Key hardcodeada de OpenRouteService (fallback)
        const val DEFAULT_ORS_API_KEY = ""
    }

    fun guardarCasa(lat: Double, lng: Double, direccion: String) {
        prefs.edit().apply {
            putString(KEY_CASA_LAT, lat.toString())
            putString(KEY_CASA_LNG, lng.toString())
            putString(KEY_CASA_DIRECCION, direccion)
            apply()
        }
    }

    fun obtenerCasaLat(): Double? {
        val lat = prefs.getString(KEY_CASA_LAT, null)
        return lat?.toDoubleOrNull()
    }

    fun obtenerCasaLng(): Double? {
        val lng = prefs.getString(KEY_CASA_LNG, null)
        return lng?.toDoubleOrNull()
    }

    fun obtenerCasaDireccion(): String? {
        return prefs.getString(KEY_CASA_DIRECCION, null)
    }

    fun tieneCasaGuardada(): Boolean {
        return prefs.getString(KEY_CASA_LAT, null) != null
    }

    fun guardarOrsApiKey(apiKey: String) {
        prefs.edit().putString(KEY_ORS_API_KEY, apiKey).apply()
    }

    fun obtenerOrsApiKey(): String {
        return prefs.getString(KEY_ORS_API_KEY, null) ?: DEFAULT_ORS_API_KEY
    }

    fun marcarOnboardingVisto() {
        prefs.edit().putBoolean(KEY_ONBOARDING_VISTO, true).apply()
    }

    fun haVistoOnboarding(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_VISTO, false)
    }

    fun borrarCasa() {
        prefs.edit().apply {
            remove(KEY_CASA_LAT)
            remove(KEY_CASA_LNG)
            remove(KEY_CASA_DIRECCION)
            apply()
        }
    }
}
