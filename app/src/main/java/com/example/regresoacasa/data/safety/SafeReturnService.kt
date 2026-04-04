package com.example.regresoacasa.data.safety

import android.content.Context
import android.content.Intent
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.domain.model.UbicacionUsuario
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Servicio para el modo "Regreso Seguro" - MVP vía Intent
 * Comparte ubicación en tiempo real vía WhatsApp/SMS
 */
class SafeReturnService(
    private val context: Context
) {
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    /**
     * Comparte ubicación actual con contacto de confianza
     */
    fun shareLocation(
        ubicacion: UbicacionUsuario,
        destino: LugarFavorito,
        etaMinutos: Int,
        contacto: String
    ) {
        val mensaje = buildLocationMessage(ubicacion, destino, etaMinutos)
        
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mensaje)
            putExtra(Intent.EXTRA_PHONE_NUMBER, contacto)
        }
        
        val shareIntent = Intent.createChooser(sendIntent, "Compartir ubicación")
        shareIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(shareIntent)
    }
    
    /**
     * Genera mensaje de "Llegué bien"
     */
    fun sendArrivalConfirmation(contacto: String, destino: LugarFavorito) {
        val mensaje = "✅ Llegué bien a: ${destino.direccion}\n" +
                     "Hora: ${dateFormat.format(Date())}"
        
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mensaje)
            putExtra(Intent.EXTRA_PHONE_NUMBER, contacto)
        }
        
        val shareIntent = Intent.createChooser(sendIntent, "Enviar confirmación")
        shareIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(shareIntent)
    }
    
    /**
     * Construye mensaje con ubicación en Google Maps
     */
    private fun buildLocationMessage(
        ubicacion: UbicacionUsuario,
        destino: LugarFavorito,
        etaMinutos: Int
    ): String {
        val mapsUrl = "https://www.google.com/maps?q=${ubicacion.latitud},${ubicacion.longitud}"
        
        return buildString {
            appendLine("🛡️ Regreso Seguro Activado")
            appendLine()
            appendLine("📍 Mi ubicación: $mapsUrl")
            appendLine()
            appendLine("🏠 Destino: ${destino.direccion}")
            appendLine("⏰ ETA: ~$etaMinutos minutos")
            appendLine()
            appendLine("Te aviso cuando llegue 👍")
        }
    }
    
    /**
     * Genera mensaje periódico con actualización
     */
    fun sendPeriodicUpdate(
        ubicacion: UbicacionUsuario,
        destino: LugarFavorito,
        etaMinutos: Int,
        distanciaMetros: Double,
        contacto: String
    ) {
        val mapsUrl = "https://www.google.com/maps?q=${ubicacion.latitud},${ubicacion.longitud}"
        val distanciaTexto = if (distanciaMetros >= 1000) {
            "%.1f km".format(distanciaMetros / 1000)
        } else {
            "%.0f m".format(distanciaMetros)
        }
        
        val mensaje = buildString {
            appendLine("🛡️ Actualización de Regreso Seguro")
            appendLine()
            appendLine("📍 Ubicación: $mapsUrl")
            appendLine("📏 Distancia restante: $distanciaTexto")
            appendLine("⏰ ETA: ~$etaMinutos min")
        }
        
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mensaje)
            putExtra(Intent.EXTRA_PHONE_NUMBER, contacto)
        }
        
        val shareIntent = Intent.createChooser(sendIntent, "Actualizar ubicación")
        shareIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(shareIntent)
    }
}
