package com.example.regresoacasa.data.model

import com.google.gson.annotations.SerializedName

data class RouteResponse(
    @SerializedName("routes")
    val routes: List<Route>,
    @SerializedName("bbox")
    val bbox: List<Double>?
)

data class Route(
    @SerializedName("summary")
    val summary: RouteSummary,
    @SerializedName("segments")
    val segments: List<Segment>,
    @SerializedName("geometry")
    val geometry: String
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

data class GeocodingResponse(
    @SerializedName("features")
    val features: List<Feature>
)

data class Feature(
    @SerializedName("geometry")
    val geometry: Geometry,
    @SerializedName("properties")
    val properties: Properties
)

data class Geometry(
    @SerializedName("coordinates")
    val coordinates: List<Double>
)

data class Properties(
    @SerializedName("name")
    val name: String?,
    @SerializedName("label")
    val label: String?
)
