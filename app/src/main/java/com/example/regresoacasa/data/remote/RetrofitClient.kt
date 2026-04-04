package com.example.regresoacasa.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val ORS_BASE_URL = "https://api.openrouteservice.org/"
    private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"

    val orsApiService: OrsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ORS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OrsApiService::class.java)
    }
    
    val nominatimApiService: NominatimApiService by lazy {
        Retrofit.Builder()
            .baseUrl(NOMINATIM_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimApiService::class.java)
    }
}
