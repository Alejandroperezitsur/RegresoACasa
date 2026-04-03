package com.example.regresoacasa.data.remote

import com.example.regresoacasa.data.model.GeocodingResponse
import com.example.regresoacasa.data.model.RouteResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface OrsApiService {

    @Headers(
        "Accept: application/json, application/geo+json, application/gpx+xml, img/png; charset=utf-8"
    )
    @GET("v2/directions/foot-walking")
    suspend fun getWalkingRoute(
        @Query("api_key") apiKey: String,
        @Query("start") start: String,
        @Query("end") end: String
    ): Response<RouteResponse>

    @Headers(
        "Accept: application/json, application/geo+json, application/gpx+xml, img/png; charset=utf-8"
    )
    @GET("v2/directions/driving-car")
    suspend fun getDrivingRoute(
        @Query("api_key") apiKey: String,
        @Query("start") start: String,
        @Query("end") end: String
    ): Response<RouteResponse>

    @Headers(
        "Accept: application/json, application/geo+json, application/gpx+xml, img/png; charset=utf-8"
    )
    @GET("geocode/search")
    suspend fun geocodeAddress(
        @Query("api_key") apiKey: String,
        @Query("text") text: String,
        @Query("size") size: Int = 1
    ): Response<GeocodingResponse>
}
