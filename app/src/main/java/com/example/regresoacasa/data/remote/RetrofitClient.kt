package com.example.regresoacasa.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val ORS_BASE_URL = "https://api.openrouteservice.org/"
    private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"

    // Timeouts más realistas para APIs geográficas
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 15L

    // Logging interceptor solo en debug
    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    // Cliente ORS con logging en debug
    private val orsClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    // Dejamos que el service defina el Accept header si existe
                    .apply {
                        if (chain.request().header("Accept") == null) {
                            header("Accept", "application/json")
                        }
                    }
                    .header("Accept-Charset", "utf-8")
                    .build()
                chain.proceed(request)
            }
        
        // Solo agregar logging en debug builds
        try {
            if (com.example.regresoacasa.BuildConfig.DEBUG) {
                builder.addInterceptor(loggingInterceptor)
            }
        } catch (e: Exception) {
            // BuildConfig puede no estar disponible en tests
        }
        
        builder.build()
    }

    // Cliente Nominatim con User-Agent requerido
    private val nominatimClient by lazy {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "RegresoACasaApp_v1.0_${System.currentTimeMillis()}_Dev (contact: alex.dev.mex@gmail.com)")
                    .header("Accept", "application/json")
                    .header("Accept-Language", "es-MX,es")
                    .header("Connection", "close") // Evitar mantener conexiones innecesarias
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        
        // Solo agregar logging en debug builds
        try {
            if (com.example.regresoacasa.BuildConfig.DEBUG) {
                builder.addInterceptor(loggingInterceptor)
            }
        } catch (e: Exception) {
            // BuildConfig puede no estar disponible en tests
        }
        
        builder.build()
    }

    val orsApiService: OrsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ORS_BASE_URL)
            .client(orsClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OrsApiService::class.java)
    }
    
    val nominatimApiService: NominatimApiService by lazy {
        Retrofit.Builder()
            .baseUrl(NOMINATIM_BASE_URL)
            .client(nominatimClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimApiService::class.java)
    }
}
