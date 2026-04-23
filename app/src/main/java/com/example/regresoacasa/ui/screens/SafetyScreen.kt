package com.example.regresoacasa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.regresoacasa.di.AppModule
import com.example.regresoacasa.ui.viewmodel.NavigationViewModel
import com.example.regresoacasa.ui.state.Pantalla

/**
 * SafetyScreen - UI orientada a seguridad (NO a mapas)
 * Estados: IDLE, EN VIAJE, ALERTA, COMPLETADO
 */
@Composable
fun SafetyScreen(
    viewModel: NavigationViewModel,
    onNavigateToContacts: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        containerColor = when {
            uiState.isSafeReturnActive -> Color(0xFF1B5E20) // Green when active
            else -> MaterialTheme.colorScheme.background
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !uiState.isSafeReturnActive -> {
                    IdleState(
                        onNavigateToContacts = onNavigateToContacts,
                        onNavigateToMap = onNavigateToMap
                    )
                }
                else -> {
                    ActiveTripState(
                        viewModel = viewModel,
                        uiState = uiState
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleState(
    onNavigateToContacts: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Shield Icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Regreso Seguro",
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Regreso Seguro",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Monitoreo inteligente durante tu trayecto a casa",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Start Safe Trip Button
        Button(
            onClick = { /* TODO: Open destination selection */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = "Iniciar Regreso Seguro",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Manage Contacts Button
        OutlinedButton(
            onClick = onNavigateToContacts,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = "Contactos de Emergencia",
                fontSize = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Features Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "🛡️ Características",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                SafetyFeature("Monitoreo en tiempo real")
                SafetyFeature("Alertas automáticas")
                SafetyFeature("Compartir ubicación")
                SafetyFeature("Botón de pánico")
            }
        }
    }
}

@Composable
private fun SafetyFeature(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveTripState(
    viewModel: NavigationViewModel,
    uiState: com.example.regresoacasa.ui.state.MainUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🛡️ Regreso Seguro",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(
                onClick = { viewModel.stopSafeTrip() }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Detener",
                    tint = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Indicator
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "En viaje",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Monitoreo activo",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Trip Info
                uiState.navigationState.destination?.let { destination ->
                    TripInfoRow("Destino", destination.direccion)
                    Spacer(modifier = Modifier.height(8.dp))
                    TripInfoRow("ETA", uiState.navigationState.eta ?: "Calculando...")
                    Spacer(modifier = Modifier.height(8.dp))
                    TripInfoRow("Distancia", "${(uiState.navigationState.remainingDistance / 1000).toInt()} km")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // PANIC BUTTON - Always visible during trip
        Button(
            onClick = { viewModel.triggerEmergency() },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD32F2F)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = "🚨 PÁNICO",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Toca para enviar alerta de emergencia",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Secondary Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { /* TODO: Share location */ },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("Compartir")
            }
            
            OutlinedButton(
                onClick = { /* TODO: View map */ },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("Ver Mapa")
            }
        }
    }
}

@Composable
private fun TripInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
    }
}
