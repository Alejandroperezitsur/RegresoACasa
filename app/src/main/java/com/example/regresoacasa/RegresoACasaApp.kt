package com.example.regresoacasa

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.regresoacasa.data.local.AppDatabase
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
        
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "regreso_a_casa_db"
        )
        .addMigrations(MIGRATION_4_5)
        .build()
        
        // Verificar si hay un error previo guardado
        val errorFile = File(filesDir, ERROR_FILE)
        if (errorFile.exists()) {
            try {
                val errorContent = errorFile.readText()
                Log.e(TAG, "Error previo encontrado: $errorContent")
                // Limpiar el archivo
                errorFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo archivo de crash", e)
            }
        }
        
        // Configurar manejo global de excepciones
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val errorMsg = "$timestamp | ${throwable.javaClass.simpleName}: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
            
            Log.e(TAG, "Excepción no controlada: $errorMsg", throwable)
            
            // Guardar error en archivo
            try {
                FileWriter(errorFile).use { writer ->
                    writer.write(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando crash", e)
            }
        }
        
        Log.d(TAG, "Aplicación iniciada")
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
}
