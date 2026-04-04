package com.example.regresoacasa.data.model

import com.google.gson.annotations.SerializedName

data class NominatimResult(
    @SerializedName("place_id") val placeId: Long? = null,
    @SerializedName("lat") val lat: String? = null,
    @SerializedName("lon") val lon: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("address") val address: NominatimAddress? = null
)

data class NominatimAddress(
    @SerializedName("road") val road: String? = null,
    @SerializedName("house_number") val houseNumber: String? = null,
    @SerializedName("suburb") val suburb: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("town") val town: String? = null,
    @SerializedName("village") val village: String? = null,
    @SerializedName("county") val county: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("postcode") val postcode: String? = null
) {
    fun formatAddress(): String {
        val parts = mutableListOf<String>()
        
        // Construir dirección principal
        val street = if (!houseNumber.isNullOrBlank() && !road.isNullOrBlank()) {
            "$road $houseNumber"
        } else if (!road.isNullOrBlank()) {
            road
        } else null
        
        street?.let { parts.add(it) }
        
        // Añadir colonia/suburb
        val neighborhood = suburb
        neighborhood?.let { parts.add(it) }
        
        // Ciudad/Pueblo
        val cityName = city ?: town ?: village
        cityName?.let { parts.add(it) }
        
        // Estado
        state?.let { parts.add(it) }
        
        return parts.joinToString(", ")
    }
}
