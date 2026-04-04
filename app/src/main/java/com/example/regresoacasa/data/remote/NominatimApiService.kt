package com.example.regresoacasa.data.remote

import com.example.regresoacasa.data.model.GeocodingResponse
import com.example.regresoacasa.data.model.NominatimResult
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApiService {
    
    @GET("search")
    suspend fun searchAddress(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1
    ): Response<List<NominatimResult>>
    
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json"
    ): Response<NominatimResult>
}
