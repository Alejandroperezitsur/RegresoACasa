package com.example.regresoacasa.domain.utils

import kotlinx.coroutines.delay

/**
 * Función de reintentos con backoff exponencial para operaciones de red
 * @param times Número máximo de intentos
 * @param initialDelay Retraso inicial en ms
 * @param maxDelay Retraso máximo en ms
 * @param factor Factor multiplicativo para el backoff
 * @param block Bloque de código a ejecutar
 */
suspend fun <T> retryIO(
    times: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 4000,
    factor: Double = 2.0,
    shouldRetryOn: (Throwable) -> Boolean = { it is java.io.IOException || it is java.net.SocketTimeoutException },
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) { attempt ->
        try {
            return block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!shouldRetryOn(e)) {
                throw e
            }
            // No retrasar en el último intento fallido
            if (attempt < times - 1) {
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
    }
    return block()
}

/**
 * Decodifica un polyline de forma segura, evitando crashes por datos malformed
 * @param encoded String codificado en formato polyline
 * @return Lista de LatLng o lista vacía si hay error
 */
fun decodePolylineSafe(encoded: String?): List<com.google.android.gms.maps.model.LatLng> {
    if (encoded.isNullOrBlank()) return emptyList()
    
    return try {
        val path = ArrayList<com.google.android.gms.maps.model.LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            
            val dlat = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            
            val dlng = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val latitude = lat / 1E5
            val longitude = lng / 1E5
            
            // Validar rangos geográficos
            if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                path.add(com.google.android.gms.maps.model.LatLng(latitude, longitude))
            }
        }
        path
    } catch (e: Exception) {
        // Logear error pero no crashar
        android.util.Log.e("DecodePolyline", "Error decodificando polyline: ${e.message}")
        emptyList()
    }
}

/**
 * Traduce instrucciones de navegación del inglés al español
 */
object InstructionTranslator {
    fun translate(text: String, locale: java.util.Locale = java.util.Locale.getDefault()): String {
        // Solo traducir si el locale es español
        if (locale.language != "es") return text
        
        return when {
            text.contains("Turn right", ignoreCase = true) -> 
                text.replace("Turn right", "Gira a la derecha", ignoreCase = true)
            text.contains("Turn left", ignoreCase = true) -> 
                text.replace("Turn left", "Gira a la izquierda", ignoreCase = true)
            text.contains("Head straight", ignoreCase = true) || 
            text.contains("Continue straight", ignoreCase = true) -> 
                text.replace(Regex("(Head|Continue) straight", RegexOption.IGNORE_CASE), "Continúa recto")
            text.contains("Arrive at", ignoreCase = true) -> 
                text.replace(Regex("Arrive at.*", RegexOption.IGNORE_CASE), "Has llegado a tu destino")
            text.contains("You have arrived", ignoreCase = true) -> 
                "Has llegado"
            text.contains("Keep right", ignoreCase = true) -> 
                text.replace("Keep right", "Mantente a la derecha", ignoreCase = true)
            text.contains("Keep left", ignoreCase = true) -> 
                text.replace("Keep left", "Mantente a la izquierda", ignoreCase = true)
            text.contains("U-turn", ignoreCase = true) -> 
                text.replace("U-turn", "Giro en U", ignoreCase = true)
            else -> text
        }
    }
    
    fun translateList(instructions: List<String>): List<String> {
        return instructions.map { translate(it) }
    }
}
