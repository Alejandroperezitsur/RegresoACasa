package com.example.regresoacasa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.regresoacasa.core.safety.state.SafetyMode

/**
 * V3 FASE 10 — UI HONESTA (CRÍTICO)
 * 
 * Banner que muestra SIEMPRE el modo real del sistema.
 * Nunca muestra estado falso al usuario.
 */
@Composable
fun SafetyModeBanner(
    mode: SafetyMode,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, iconColor, message) = when (mode) {
        SafetyMode.FULL -> {
            Triple(
                Color(0xFF4CAF50), // Verde
                Color.White,
                "Sistema operativo normal"
            )
        }
        SafetyMode.NO_INTERNET -> {
            Triple(
                Color(0xFFFF9800), // Naranja
                Color.White,
                "⚠️ Sin internet - Solo GPS activo"
            )
        }
        SafetyMode.LOW_GPS -> {
            Triple(
                Color(0xFFFF5722), // Naranja oscuro
                Color.White,
                "🔴 Precisión GPS baja - No confiar en ruta"
            )
        }
        SafetyMode.SMS_ONLY -> {
            Triple(
                Color(0xFFF44336), // Rojo
                Color.White,
                "🚨 Sin GPS - Solo envío de alertas"
            )
        }
        SafetyMode.CRITICAL -> {
            Triple(
                Color(0xFFB71C1C), // Rojo oscuro
                Color.White,
                "⛔ MODO CRÍTICO - Funcionalidad mínima"
            )
        }
    }
    
    // Solo mostrar banner si no es modo FULL
    if (mode != SafetyMode.FULL) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Advertencia",
                    tint = iconColor,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Indicador de confiabilidad del sistema
 */
@Composable
fun ReliabilityIndicator(
    reliabilityScore: Int, // 0-100
    modifier: Modifier = Modifier
) {
    val (color, label) = when {
        reliabilityScore >= 80 -> {
            Pair(Color(0xFF4CAF50), "Alta")
        }
        reliabilityScore >= 50 -> {
            Pair(Color(0xFFFF9800), "Media")
        }
        else -> {
            Pair(Color(0xFFF44336), "Baja")
        }
    }
    
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "Confiabilidad: $label ($reliabilityScore%)",
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}
