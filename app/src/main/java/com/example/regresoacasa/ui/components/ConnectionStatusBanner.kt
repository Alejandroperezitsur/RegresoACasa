package com.example.regresoacasa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.regresoacasa.ui.state.ConnectionState
import com.example.regresoacasa.ui.state.CriticalStateColor
import com.example.regresoacasa.ui.state.toInfo

@Composable
fun ConnectionStatusBanner(
    state: ConnectionState,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val info = state.toInfo()
    
    if (state is ConnectionState.Connected) return
    
    val backgroundColor = when (info.color) {
        CriticalStateColor.ERROR -> Color(0xFFB71C1C)
        CriticalStateColor.WARNING -> Color(0xFFF57C00)
        CriticalStateColor.INFO -> Color(0xFF1565C0)
    }
    
    AnimatedVisibility(
        visible = true,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = when (state) {
                            is ConnectionState.NoInternet -> Icons.Default.CloudOff
                            is ConnectionState.GpsLost -> Icons.Default.GpsOff
                            is ConnectionState.LowBattery -> Icons.Default.BatteryAlert
                            is ConnectionState.ApiError -> Icons.Default.Error
                            else -> Icons.Default.Error
                        },
                        contentDescription = null,
                        tint = Color.White
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = info.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                
                if (info.actionLabel != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (info.isDismissible) {
                            TextButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color.White.copy(alpha = 0.7f)
                                )
                            ) {
                                Text("Cerrar")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        Button(
                            onClick = onAction,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = backgroundColor
                            )
                        ) {
                            if (state is ConnectionState.GpsLost) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            Text(info.actionLabel)
                        }
                    }
                }
            }
        }
    }
}
