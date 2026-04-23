package com.example.regresoacasa

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.regresoacasa.data.local.AppDatabase
import com.example.regresoacasa.data.local.MIGRATION_5_6
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
    }

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        
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
        
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "regreso_a_casa_db"
        )
        .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
        .build()
        
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
}
