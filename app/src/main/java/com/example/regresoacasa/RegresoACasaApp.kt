package com.example.regresoacasa

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.regresoacasa.core.SafeReturnEngine
import com.example.regresoacasa.core.security.SecurityManager
import com.example.regresoacasa.data.local.AppDatabase
import com.example.regresoacasa.data.local.MIGRATION_5_6
import com.example.regresoacasa.data.local.MIGRATION_6_7
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RegresoACasaApp : Application() {

    companion object {
        private const val TAG = "RegresoACasaApp"
        private const val ERROR_FILE = "crash_error.txt"
        
        @Volatile
        private var instance: RegresoACasaApp? = null
        
        fun getInstance(): RegresoACasaApp {
            return instance ?: throw IllegalStateException("App not initialized")
        }
    }

    lateinit var database: AppDatabase
        private set
    
    var isApiKeyValid: Boolean = false
        private set
    
    // SafeReturnEngine - Single Source of Truth
    lateinit var safeReturnEngine: SafeReturnEngine
        private set
    
    // SecurityManager - Android Keystore
    lateinit var securityManager: SecurityManager
        private set
    
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        
        instance = this
        
        // FASE 2: Validación de API Key - ANTI-FALLO SILENCIOSO
        isApiKeyValid = validateApiKey()
        if (!isApiKeyValid) {
            Timber.e("API KEY INVÁLIDA - Navegación bloqueada")
        }
        
        // Initialize Timber for structured logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashlyticsTree())
        }
        
        // Initialize Firebase Crashlytics
        try {
            FirebaseCrashlytics.getInstance().setCustomKey("app_version", BuildConfig.VERSION_NAME)
            FirebaseCrashlytics.getInstance().setCustomKey("build_type", if (BuildConfig.DEBUG) "debug" else "release")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Crashlytics")
        }
        
        // FASE 13: Encriptar database con SQLCipher
        // La clave de encriptación debe ser derivada de algo seguro en producción
        // Para desarrollo, usamos una clave fija (NO usar en producción)
        val encryptionKey = if (BuildConfig.DEBUG) {
            "regreso_a_casa_dev_key_32chars!!".toCharArray()
        } else {
            // En producción, usar Android Keystore para derivar la clave
            // Por ahora, usamos una clave basada en el device ID
            getDeviceSpecificKey().toCharArray()
        }
        
        val supportFactory = SupportFactory(SQLiteDatabase.getBytes(encryptionKey))
        
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "regreso_a_casa_db"
        )
        .openHelperFactory(supportFactory)
        .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        .build()
        
        // Initialize SecurityManager with Android Keystore
        securityManager = SecurityManager(applicationContext)
        
        // Initialize SafeReturnEngine - Single Source of Truth
        val backendUrl = BuildConfig.BACKEND_PROXY_URL.ifBlank { "https://your-backend.com" }
        safeReturnEngine = SafeReturnEngine(
            context = applicationContext,
            scope = appScope,
            backendUrl = backendUrl
        )
        
        // Verificar si hay un error previo guardado
        val errorFile = File(filesDir, ERROR_FILE)
        if (errorFile.exists()) {
            try {
                val errorContent = errorFile.readText()
                Timber.e("Error previo encontrado: $errorContent")
                // Limpiar el archivo
                errorFile.delete()
            } catch (e: Exception) {
                Timber.e(e, "Error leyendo archivo de crash")
            }
        }
        
        // Configurar manejo global de excepciones
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val errorMsg = "$timestamp | ${throwable.javaClass.simpleName}: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
            
            Timber.e(throwable, "Excepción no controlada: $errorMsg")
            
            // Report to Crashlytics
            try {
                FirebaseCrashlytics.getInstance().recordException(throwable)
            } catch (e: Exception) {
                Timber.e(e, "Failed to report to Crashlytics")
            }
            
            // Guardar error en archivo
            try {
                FileWriter(errorFile).use { writer ->
                    writer.write(errorMsg)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error guardando crash")
            }
        }
        
        Timber.d("Aplicación iniciada")
    }
    
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS alert_deliveries (
                    alertId TEXT PRIMARY KEY NOT NULL,
                    contactPhone TEXT NOT NULL,
                    message TEXT NOT NULL,
                    sendStatus TEXT NOT NULL,
                    deliveryStatus TEXT NOT NULL,
                    retryCount INTEGER NOT NULL,
                    sentTimestamp INTEGER NOT NULL,
                    deliveredTimestamp INTEGER,
                    locationLat REAL,
                    locationLng REAL,
                    batteryLevel INTEGER,
                    tripId TEXT
                )
            """)
        }
    }
    
    private class CrashlyticsTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG) {
                return
            }
            
            if (t != null) {
                FirebaseCrashlytics.getInstance().recordException(t)
            } else {
                FirebaseCrashlytics.getInstance().log(message)
            }
        }
    }
    
    /**
     * FASE 2: Validación de API Key - ANTI-FALLO SILENCIOSO
     * Valida que la API key de OpenRouteService esté configurada correctamente
     */
    private fun validateApiKey(): Boolean {
        val key = BuildConfig.ORS_API_KEY
        val isValid = key.isNotBlank() && key.length > 40
        Timber.d("API Key validation: ${if (isValid) "VALID" else "INVALID"} (length: ${key.length})")
        return isValid
    }
    
    /**
     * Genera una clave específica del dispositivo para encriptación con SQLCipher
     * Usa SecurityManager para obtener clave real de Android Keystore
     */
    private fun getDeviceSpecificKey(): String {
        return try {
            val securityMgr = SecurityManager(applicationContext)
            val masterKey = kotlinx.coroutines.runBlocking {
                securityMgr.getOrCreateMasterKey()
            }
            // Convertir clave a string de 32 caracteres para SQLCipher
            val keyBytes = masterKey.encoded
            val hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(keyBytes)
                .joinToString("") { "%02x".format(it) }
            hash.take(32)
        } catch (e: Exception) {
            Timber.e(e, "Error generando clave con SecurityManager, usando fallback")
            // Fallback seguro para desarrollo
            if (BuildConfig.DEBUG) {
                "regreso_a_casa_dev_key_32chars!!"
            } else {
                val deviceId = android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                val combined = deviceId + "regreso_a_casa_salt_2024"
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(combined.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                hash.take(32)
            }
        }
    }
}
