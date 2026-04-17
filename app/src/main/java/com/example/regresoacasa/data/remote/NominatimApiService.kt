package com.example.regresoacasa.data.remote

import com.example.regresoacasa.data.model.GeocodingResponse
import com.example.regresoacasa.data.model.NominatimResult
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApiService {
    
    /**
     * Busca direcciones con bias geográfico hacia México
     * @param query Texto a buscar
     * @param format Formato de respuesta (json)
     * @param limit Máximo de resultados (default: 5, max: 10)
     * @param countrycodes Código de país ISO 3166-1 alpha-2 (MX para México)
     * @param viewbox Límite de vista para bias geográfico (México: -118,14.5,-86,32.7)
     * @param bounded Si debe restringir resultados al viewbox
     * @param acceptLanguage Idioma preferido para resultados
     */
    @GET("search")
    suspend fun searchAddress(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 10,
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("namedetails") nameDetails: Int = 1,
        @Query("accept-language") acceptLanguage: String = "es"
    ): Response<List<NominatimResult>>
    
    /**
     * Búsqueda estructurada (más precisa para direcciones mexicanas)
     * @param street Calle y número
     * @param city Ciudad
     * @param state Estado
     * @param country País (México)
     * @param format Formato de respuesta
     * @param limit Máximo de resultados
     */
    @GET("search")
    suspend fun searchStructured(
        @Query("street") street: String,
        @Query("city") city: String? = null,
        @Query("state") state: String? = null,
        @Query("country") country: String = "México",
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 5,
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("accept-language") acceptLanguage: String = "es-MX,es"
    ): Response<List<NominatimResult>>
    
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("accept-language") acceptLanguage: String = "es-MX,es"
    ): Response<NominatimResult>
}
