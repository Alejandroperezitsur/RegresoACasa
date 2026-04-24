package com.example.regresoacasa.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.regresoacasa.BuildConfig
import com.example.regresoacasa.data.local.AppDatabase
import com.example.regresoacasa.data.local.CachedRouteDao
import com.example.regresoacasa.data.local.LugarFavoritoDao
import com.example.regresoacasa.data.local.MapStateManager
import com.example.regresoacasa.data.local.MIGRATION_5_6
import com.example.regresoacasa.data.local.SearchHistoryDao
import com.example.regresoacasa.core.safety.alert.SmsManagerWrapper
import com.example.regresoacasa.core.safety.alert.AlertPersistence
import com.example.regresoacasa.core.safety.persistence.SafetyPersistence
import com.example.regresoacasa.data.location.LocationTrackingService
import com.example.regresoacasa.data.location.RobustLocationFilter
import com.example.regresoacasa.data.network.NetworkMonitor
import com.example.regresoacasa.data.remote.NominatimApiService
import com.example.regresoacasa.data.remote.OrsApiService
import com.example.regresoacasa.data.remote.RetrofitClient
import com.example.regresoacasa.data.repository.MapRepositoryImpl
import com.example.regresoacasa.domain.repository.MapRepository
import com.example.regresoacasa.domain.usecase.BuscarLugaresUseCase
import com.example.regresoacasa.domain.usecase.CalcularRutaUseCase
import com.example.regresoacasa.domain.usecase.GuardarFavoritoUseCase
import com.example.regresoacasa.domain.usecase.ObtenerCasaUseCase
import com.example.regresoacasa.domain.usecase.ObtenerDireccionUseCase
import com.example.regresoacasa.domain.usecase.ObtenerFavoritosUseCase
import com.example.regresoacasa.domain.usecase.ObtenerUbicacionUseCase

/**
 * Simple DI container (sin Hilt/Koin para mantener simplicidad)
 */
class AppModule private constructor(context: Context) {

    val appContext = context.applicationContext

    // Migration from version 2 to 3 (add search_history table)
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS search_history (
                    id TEXT PRIMARY KEY NOT NULL,
                    query TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    latitud REAL NOT NULL,
                    longitud REAL NOT NULL,
                    timestamp INTEGER NOT NULL,
                    searchCount INTEGER NOT NULL DEFAULT 1
                )
            """)
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_search_history_query ON search_history(query)")
        }
    }

    // Use app database from Application class
    val database: AppDatabase = (appContext as com.example.regresoacasa.RegresoACasaApp).database

    val lugarFavoritoDao: LugarFavoritoDao = database.lugarFavoritoDao()
    val cachedRouteDao: CachedRouteDao = database.cachedRouteDao()
    val searchHistoryDao: SearchHistoryDao = database.searchHistoryDao()

    // Location Tracking
    val locationTrackingService: LocationTrackingService by lazy {
        LocationTrackingService(appContext)
    }
    
    val robustLocationFilter: RobustLocationFilter by lazy {
        RobustLocationFilter()
    }
    
    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(appContext)
    }
    
    val mapStateManager: MapStateManager by lazy {
        MapStateManager(appContext)
    }

    // API Services
    val nominatimApi: NominatimApiService = RetrofitClient.nominatimApiService
    val orsApi: OrsApiService = RetrofitClient.orsApiService

    // Repository
    val mapRepository: MapRepository by lazy {
        MapRepositoryImpl(
            context = appContext,
            lugarFavoritoDao = lugarFavoritoDao,
            cachedRouteDao = cachedRouteDao,
            nominatimApi = nominatimApi,
            orsApi = orsApi,
            apiKey = BuildConfig.ORS_API_KEY
        )
    }

    // Use Cases
    val buscarLugaresUseCase: BuscarLugaresUseCase by lazy {
        BuscarLugaresUseCase(mapRepository)
    }

    val calcularRutaUseCase: CalcularRutaUseCase by lazy {
        CalcularRutaUseCase(mapRepository)
    }

    val obtenerFavoritosUseCase: ObtenerFavoritosUseCase by lazy {
        ObtenerFavoritosUseCase(mapRepository)
    }

    val guardarFavoritoUseCase: GuardarFavoritoUseCase by lazy {
        GuardarFavoritoUseCase(mapRepository)
    }

    val obtenerCasaUseCase: ObtenerCasaUseCase by lazy {
        ObtenerCasaUseCase(mapRepository)
    }

    val obtenerUbicacionUseCase: ObtenerUbicacionUseCase by lazy {
        ObtenerUbicacionUseCase(mapRepository)
    }

    val obtenerDireccionUseCase: ObtenerDireccionUseCase by lazy {
        ObtenerDireccionUseCase(mapRepository)
    }

    // SMS Wrapper con delivery confirmation
    val safetyPersistence: SafetyPersistence by lazy {
        SafetyPersistence(appContext)
    }

    val smsManagerWrapper: SmsManagerWrapper by lazy {
        SmsManagerWrapper(
            context = appContext,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job()),
            persistence = safetyPersistence
        )
    }

    companion object {
        @Volatile
        private var instance: AppModule? = null

        fun getInstance(context: Context): AppModule {
            return instance ?: synchronized(this) {
                instance ?: AppModule(context).also { instance = it }
            }
        }
    }
}
