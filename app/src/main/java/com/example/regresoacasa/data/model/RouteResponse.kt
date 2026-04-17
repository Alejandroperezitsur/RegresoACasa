package com.example.regresoacasa.data.model

import com.google.gson.annotations.SerializedName

/**
 * Representa la respuesta de OpenRouteService en formato GeoJSON (FeatureCollection).
 */
data class RouteResponse(
    @SerializedName("features")
    val features: List<RouteFeature>,
    @SerializedName("bbox")
    val bbox: List<Double>?
)

data class RouteFeature(
    @SerializedName("geometry")
    val geometry: GeometryData,
    @SerializedName("properties")
    val properties: RouteProperties
)

data class GeometryData(
    @SerializedName("coordinates")
    val coordinates: List<List<Double>>,
    @SerializedName("type")
    val type: String
)

data class RouteProperties(
    @SerializedName("summary")
    val summary: RouteSummary,
    @SerializedName("segments")
    val segments: List<Segment>
)

data class RouteSummary(
    @SerializedName("distance")
    val distance: Double,
    @SerializedName("duration")
    val duration: Double
)

data class Segment(
    @SerializedName("distance")
    val distance: Double,
    @SerializedName("duration")
    val duration: Double,
    @SerializedName("steps")
    val steps: List<Step>
)

data class Step(
    @SerializedName("distance")
    val distance: Double,
    @SerializedName("duration")
    val duration: Double,
    @SerializedName("instruction")
    val instruction: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: Int
)

/**
 * Estructura para Geocoding (también usa Features pero es distinta a Directions)
 */
data class GeocodingResponse(
    @SerializedName("features")
    val features: List<GeocodingFeature>
)

data class GeocodingFeature(
    @SerializedName("geometry")
    val geometry: PointGeometry,
    @SerializedName("properties")
    val properties: GeocodingProperties
)

data class PointGeometry(
    @SerializedName("coordinates")
    val coordinates: List<Double>
)

data class GeocodingProperties(
    @SerializedName("name")
    val name: String?,
    @SerializedName("label")
    val label: String?
)
