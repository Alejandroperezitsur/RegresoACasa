package com.example.regresoacasa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Indicador visual de calidad de señal GPS
 */
enum class GpsSignalQuality {
    GOOD,      // Precisión < 15m
    MEDIUM,    // Precisión 15-20m
    POOR       // Precisión > 20m o sin señal
}

@Composable
fun GpsSignalIndicator(
    accuracy: Float?,
    hasSignal: Boolean,
    modifier: Modifier = Modifier
) {
    val quality = when {
        !hasSignal -> GpsSignalQuality.POOR
        accuracy == null -> GpsSignalQuality.POOR
        accuracy < 15f -> GpsSignalQuality.GOOD
        accuracy < 20f -> GpsSignalQuality.MEDIUM
        else -> GpsSignalQuality.POOR
    }
    
    val showIndicator = quality != GpsSignalQuality.GOOD
    
    AnimatedVisibility(
        visible = showIndicator,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (quality) {
                    GpsSignalQuality.GOOD -> Color.Transparent
                    GpsSignalQuality.MEDIUM -> Color(0xFFFFA000).copy(alpha = 0.9f)
                    GpsSignalQuality.POOR -> Color(0xFFD32F2F).copy(alpha = 0.9f)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Icono de estado
                Icon(
                    imageVector = when (quality) {
                        GpsSignalQuality.GOOD -> Icons.Default.GpsNotFixed
                        GpsSignalQuality.MEDIUM -> Icons.Default.GpsNotFixed
                        GpsSignalQuality.POOR -> Icons.Default.GpsOff
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                
                // Indicador visual de barras
                GpsSignalBars(quality)
                
                Spacer(modifier = Modifier.width(4.dp))
                
                // Texto
                Text(
                    text = when (quality) {
                        GpsSignalQuality.GOOD -> ""
                        GpsSignalQuality.MEDIUM -> "Señal GPS regular"
                        GpsSignalQuality.POOR -> if (hasSignal) "Señal GPS débil" else "GPS no disponible"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun GpsSignalBars(quality: GpsSignalQuality) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val activeBars = when (quality) {
            GpsSignalQuality.GOOD -> 4
            GpsSignalQuality.MEDIUM -> 2
            GpsSignalQuality.POOR -> 1
        }
        
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4 + index * 3).dp)
                    .clip(CircleShape)
                    .background(
                        if (index < activeBars) Color.White else Color.White.copy(alpha = 0.3f)
                    )
            )
        }
    }
}
