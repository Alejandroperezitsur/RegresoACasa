package com.example.regresoacasa.data.location

import android.content.Context
import org.osmdroid.config.Configuration

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
            
            // Habilitar caché
            tileSourcesCacheEnabled = true
            
            // Usar almacenamiento interno para caché
            setTileFileSystemCache("/tiles")
        }
    }
    
    /**
     * Limpia la caché de tiles
     */
    fun clearCache(context: Context) {
        try {
            val cacheDir = android.os.FileUtils.buildExternalDirectoriesAppDataDirectory(
                context, 
                "osmdroid"
            )
            cacheDir?.deleteRecursively()
        } catch (e: Exception) {
            android.util.Log.e("OsmdroidConfig", "Error limpiando caché: ${e.message}")
        }
    }
}
