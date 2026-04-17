package com.example.regresoacasa.data.location

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Configuración optimizada para OSMDroid
 * - Caché de tiles (500MB)
 * - User-Agent correcto
 * - Modo offline parcial
 */
object OsmdroidConfig {
    
    fun init(context: Context) {
        Configuration.getInstance().apply {
            // User-Agent requerido por OSM
            userAgentValue = "${context.packageName}/1.0"
            
            // Caché de tiles: 500MB máximo
            tileFileSystemCacheMaxBytes = 500L * 1024 * 1024
            
            // Directorio de caché
            val cacheDir = File(context.cacheDir, "osmdroid/tiles")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            osmdroidTileCache = cacheDir
        }
    }
    
    /**
     * Limpia la caché de tiles
     */
    fun clearCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "osmdroid")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            android.util.Log.e("OsmdroidConfig", "Error limpiando caché: ${e.message}")
        }
    }
}
