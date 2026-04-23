package com.example.regresoacasa.data.safety

import android.content.Context
import com.example.regresoacasa.domain.model.UbicacionUsuario
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertMessageFormatter(private val context: Context) {
    
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun formatCriticalAlert(
        userName: String,
        location: UbicacionUsuario?,
        stopDurationMinutes: Int,
        batteryLevel: Int,
        tripDestination: String?
    ): String {
        val mapsUrl = location?.let { "https://www.google.com/maps?q=${it.latitud},${it.longitud}" }
        
        return buildString {
            appendLine("🚨 ALERTA CRÍTICA")
            appendLine()
            appendLine("Usuario: $userName")
            appendLine("Estado: SIN RESPUESTA")
            appendLine()
            if (mapsUrl != null) {
                appendLine("Última ubicación: $mapsUrl")
                appendLine("Precisión GPS: ${location.precision?.toInt() ?: 0}m")
            } else {
                appendLine("Última ubicación: No disponible")
            }
            appendLine("Tiempo sin movimiento: $stopDurationMinutes min")
            appendLine("Batería: $batteryLevel%")
            if (tripDestination != null) {
                appendLine("Destino: $tripDestination")
            }
            appendLine()
            appendLine("Hora: ${dateTimeFormat.format(Date())}")
            appendLine()
            appendLine("⚠️ Por favor verifica su estado inmediatamente")
        }
    }

    fun formatDangerAlert(
        userName: String,
        location: UbicacionUsuario?,
        riskFactors: String,
        batteryLevel: Int
    ): String {
        val mapsUrl = location?.let { "https://www.google.com/maps?q=${it.latitud},${it.longitud}" }
        
        return buildString {
            appendLine("⚠️ ALERTA DE PELIGRO")
            appendLine()
            appendLine("Usuario: $userName")
            appendLine("Estado: SITUACIÓN ANORMAL")
            appendLine()
            if (mapsUrl != null) {
                appendLine("Ubicación: $mapsUrl")
            }
            appendLine("Factores de riesgo:")
            appendLine(riskFactors)
            appendLine()
            appendLine("Batería: $batteryLevel%")
            appendLine("Hora: ${dateTimeFormat.format(Date())}")
        }
    }

    fun formatWarningAlert(
        userName: String,
        location: UbicacionUsuario?,
        warningReason: String
    ): String {
        val mapsUrl = location?.let { "https://www.google.com/maps?q=${it.latitud},${it.longitud}" }
        
        return buildString {
            appendLine("⚡ ALERTA DE PRECAUCIÓN")
            appendLine()
            appendLine("Usuario: $userName")
            appendLine()
            if (mapsUrl != null) {
                appendLine("Ubicación: $mapsUrl")
            }
            appendLine("Motivo: $warningReason")
            appendLine("Hora: ${dateTimeFormat.format(Date())}")
        }
    }

    fun formatTripStart(
        userName: String,
        destination: String,
        etaMinutes: Int,
        trackingLink: String
    ): String {
        return buildString {
            appendLine("🛡️ Regreso Seguro Activado")
            appendLine()
            appendLine("Usuario: $userName")
            appendLine("Destino: $destination")
            appendLine("ETA aproximada: ~$etaMinutes minutos")
            appendLine()
            appendLine("🔗 Seguimiento en vivo: $trackingLink")
            appendLine()
            appendLine("Te avisaré cuando llegue 👍")
            appendLine("Hora inicio: ${dateTimeFormat.format(Date())}")
        }
    }

    fun formatTripArrival(
        userName: String,
        destination: String,
        durationMinutes: Int
    ): String {
        return buildString {
            appendLine("✅ Llegué bien a mi destino")
            appendLine()
            appendLine("Usuario: $userName")
            appendLine("Destino: $destination")
            appendLine("Duración del viaje: $durationMinutes minutos")
            appendLine()
            appendLine("Gracias por estar pendiente 👍")
            appendLine("Hora llegada: ${dateTimeFormat.format(Date())}")
        }
    }

    fun formatCheckInRequest(
        userName: String,
        timeSinceLastCheckIn: Int
    ): String {
        return buildString {
            appendLine("🔔 Verificación de Seguridad")
            appendLine()
            appendLine("Usuario: $userName")
            appendLine("Tiempo sin actividad: $timeSinceLastCheckIn minutos")
            appendLine()
            appendLine("Por favor responde para confirmar que estás bien")
            appendLine("Hora: ${dateTimeFormat.format(Date())}")
        }
    }

    fun formatLowBatteryAlert(
        userName: String,
        batteryLevel: Int,
        location: UbicacionUsuario?
    ): String {
        val mapsUrl = location?.let { "https://www.google.com/maps?q=${it.latitud},${it.longitud}" }
        
        return buildString {
            appendLine("🔋 Alerta de Batería Baja")
            appendLine()
            appendLine("Usuario: $userName")
            appendLine("Nivel de batería: $batteryLevel%")
            appendLine()
            if (mapsUrl != null) {
                appendLine("Última ubicación: $mapsUrl")
            }
            appendLine("Hora: ${dateTimeFormat.format(Date())}")
        }
    }

    fun formatSignalLossAlert(
        userName: String,
        signalLossDuration: Int,
        lastKnownLocation: UbicacionUsuario?
    ): String {
        val mapsUrl = lastKnownLocation?.let { "https://www.google.com/maps?q=${it.latitud},${it.longitud}" }
        
        return buildString {
            appendLine("📡 Pérdida de Señal")
            appendLine()
            appendLine("Usuario: $userName")
            appendLine("Tiempo sin señal: $signalLossDuration minutos")
            appendLine()
            if (mapsUrl != null) {
                appendLine("Última ubicación conocida: $mapsUrl")
            }
            appendLine("Hora: ${dateTimeFormat.format(Date())}")
        }
    }

    fun formatDeviationAlert(
        userName: String,
        deviationMeters: Double,
        currentLocation: UbicacionUsuario?,
        expectedRoute: String
    ): String {
        val mapsUrl = currentLocation?.let { "https://www.google.com/maps?q=${it.latitud},${it.longitud}" }
        
        return buildString {
            appendLine("🧭 Desviación de Ruta Detectada")
            appendLine()
            appendLine("Usuario: $userName")
            appendLine("Desviación: ${deviationMeters.toInt()} metros")
            appendLine("Ruta esperada: $expectedRoute")
            appendLine()
            if (mapsUrl != null) {
                appendLine("Ubicación actual: $mapsUrl")
            }
            appendLine("Hora: ${dateTimeFormat.format(Date())}")
        }
    }
}
