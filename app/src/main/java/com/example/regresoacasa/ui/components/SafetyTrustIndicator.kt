package com.example.regresoacasa.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.regresoacasa.data.safety.RiskEvaluator

@Composable
fun SafetyTrustIndicator(
    riskLevel: RiskEvaluator.RiskLevel,
    isMonitoring: Boolean,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (riskLevel) {
        RiskEvaluator.RiskLevel.NORMAL -> Color(0xFF4CAF50) to "Todo va bien"
        RiskEvaluator.RiskLevel.WARNING -> Color(0xFFFF9800) to "Precaución"
        RiskEvaluator.RiskLevel.DANGER -> Color(0xFFF44336) to "Peligro"
        RiskEvaluator.RiskLevel.CRITICAL -> Color(0xFFD32F2F) to "CRÍTICO"
    }

    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isMonitoring) {
            HeartbeatIndicator(color = color)
        }
        
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun HeartbeatIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(12.dp)
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun MonitoringStatusCard(
    isMonitoring: Boolean,
    lastUpdate: String,
    gpsAccuracy: Float,
    batteryLevel: Int,
    signalQuality: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isMonitoring) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isMonitoring) "🛡️ Monitoreo Activo" else "⏸️ Monitoreo Pausado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isMonitoring) {
                    HeartbeatIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            Divider()

            StatusRow("Última actualización", lastUpdate)
            StatusRow("Precisión GPS", "${gpsAccuracy.toInt()}m")
            StatusRow("Batería", "$batteryLevel%")
            StatusRow("Señal", signalQuality)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AlertCountdown(
    remainingSeconds: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember(remainingSeconds) { remainingSeconds / 60f }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️ Alerta en progreso",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Enviando alerta en ${remainingSeconds}s...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Cancelar")
            }
        }
    }
}
