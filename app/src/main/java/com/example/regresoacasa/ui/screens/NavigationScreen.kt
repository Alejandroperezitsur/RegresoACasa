package com.example.regresoacasa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.regresoacasa.ui.components.MapaView
import com.example.regresoacasa.ui.state.ConnectionState
import com.example.regresoacasa.ui.state.NavigationUiState
import com.example.regresoacasa.ui.state.SystemFeedbackState
import com.example.regresoacasa.ui.viewmodel.NavigationViewModel

/**
 * NavigationScreen v2.0 - UI Mínima según especificación de producto
 * Las 3 cosas que el usuario ve SIEMPRE:
 * 1. Instrucción actual grande
 * 2. Distancia a próxima maniobra
 * 3. Barra de progreso
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    viewModel: NavigationViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigationState = uiState.navigationState
    val connectionState = uiState.connectionState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navegación") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1565C0),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mapa (contexto secundario, 40% de atención)
            MapaView(
                ubicacion = navigationState.userLocation,
                destino = navigationState.destination,
                ruta = navigationState.route?.puntos,
                isFollowingUser = navigationState.isFollowingUser,
                onFollowUserToggle = { viewModel.toggleFollowUser() }
            )

            // UI Mínima - Capa principal
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // BANNER DE ESTADO (Sistema de Confianza)
                StatusBanner(
                    connectionState = connectionState,
                    isOffRoute = navigationState.isOffRoute,
                    isRecalculating = uiState.estaCalculandoRuta,
                    hasGpsSignal = uiState.hasGpsSignal,
                    gpsAccuracy = uiState.gpsAccuracy,
                    isSafeReturnActive = uiState.isSafeReturnActive,
                    systemFeedback = navigationState.systemFeedback
                )

                Spacer(modifier = Modifier.weight(1f))

                // CARD PRINCIPAL - Las 3 cosas que el usuario ve SIEMPRE
                TurnByTurnCard(
                    instruction = navigationState.currentInstruction?.textoCorto ?: "Continúa recto",
                    distance = navigationState.currentInstruction?.distanciaFormateada ?: "${navigationState.remainingDistance.toInt()} m",
                    progress = navigationState.progressToNextTurn,
                    icon = getInstructionIcon(navigationState.currentInstruction?.tipo?.name ?: "CONTINUA_RECTO"),
                    isImminent = navigationState.distanceToNextTurn < 50
                )

                // Info secundaria (Guardian, tiempo total)
                SecondaryInfoRow(
                    isSafeReturnActive = uiState.isSafeReturnActive,
                    remainingDuration = navigationState.remainingDuration,
                    remainingDistance = navigationState.remainingDistance
                )
            }

            // Loading overlay
            if (uiState.estaCalculandoRuta) {
                CalculatingOverlay()
            }

            // Llegada detectada
            if (navigationState.hasArrived) {
                ArrivalCelebration(
                    duration = navigationState.elapsedTime,
                    distance = navigationState.totalDistance,
                    onShare = { viewModel.shareArrival() },
                    onDismiss = { viewModel.dismissArrival() }
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    connectionState: ConnectionState,
    isOffRoute: Boolean,
    isRecalculating: Boolean,
    hasGpsSignal: Boolean,
    gpsAccuracy: Float?,
    isSafeReturnActive: Boolean,
    systemFeedback: SystemFeedbackState
) {
    when {
        isRecalculating -> {
            Banner(
                icon = Icons.Default.Refresh,
                text = "Calculando nueva ruta...",
                color = Color(0xFF2196F3),
                showSpinner = true
            )
        }
        isOffRoute -> {
            Banner(
                icon = Icons.Default.Warning,
                text = "Desviado - Espera un momento",
                color = Color(0xFFFF5722)
            )
        }
        !hasGpsSignal || (gpsAccuracy != null && gpsAccuracy > 30f) -> {
            Banner(
                icon = Icons.Default.GpsOff,
                text = "GPS: Precisión baja (~${gpsAccuracy?.toInt() ?: 50}m)",
                color = Color(0xFFFFA726)
            )
        }
        systemFeedback is SystemFeedbackState.HapticsUnavailable -> {
            Banner(
                icon = Icons.Default.Warning,
                text = "Modo silencioso activo (sin vibración)",
                color = Color(0xFFFFC107)
            )
        }
        isSafeReturnActive -> {
            Banner(
                icon = Icons.Default.Shield,
                text = "🛡️ Guardian activo - Contacto informado",
                color = Color(0xFF4CAF50)
            )
        }
        else -> {
            // Sin banner = todo bien (silencio = confianza)
        }
    }
}

@Composable
private fun Banner(
    icon: ImageVector,
    text: String,
    color: Color,
    showSpinner: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (showSpinner) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun TurnByTurnCard(
    instruction: String,
    distance: String,
    progress: Float,
    icon: ImageVector,
    isImminent: Boolean
) {
    val bgColor = if (isImminent) Color(0xFF1565C0) else Color.White
    val textColor = if (isImminent) Color.White else Color(0xFF1565C0)
    val subtitleColor = if (isImminent) Color.White.copy(alpha = 0.8f) else Color.Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icono de instrucción
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // (1) INSTRUCCIÓN PRINCIPAL - Grande y clara
            Text(
                text = instruction,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // (2) DISTANCIA - Subtítulo
            Text(
                text = "en $distance",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = subtitleColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            // (3) BARRA DE PROGRESO
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (isImminent) Color.White else Color(0xFF1565C0),
                trackColor = if (isImminent) Color.White.copy(alpha = 0.3f) else Color.LightGray,
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
private fun SecondaryInfoRow(
    isSafeReturnActive: Boolean,
    remainingDuration: Double,
    remainingDistance: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Guardian indicator (si activo)
        if (isSafeReturnActive) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Protegido",
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50)
                )
            }
        }

        // Tiempo/distancia total (info secundaria)
        val minutos = (remainingDuration / 60).toInt()
        val distanciaKm = remainingDistance / 1000

        Text(
            text = if (distanciaKm >= 1) {
                "${distanciaKm.toInt()} km • ${minutos} min restantes"
            } else {
                "${remainingDistance.toInt()} m • ${minutos} min restantes"
            },
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun CalculatingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color(0xFF1565C0))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Calculando ruta...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ArrivalCelebration(
    duration: Long,
    distance: Double,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉",
                    fontSize = 64.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "¡Llegaste a casa!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0)
                )

                Spacer(modifier = Modifier.height(8.dp))

                val minutos = (duration / 1000 / 60).toInt()
                val distanciaKm = distance / 1000

                Text(
                    text = "${minutos} minutos • %.1f km".format(distanciaKm),
                    fontSize = 16.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Shield, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compartir que llegué bien")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}

private fun getInstructionIcon(type: String): ImageVector {
    return when (type) {
        "GIRA_DERECHA" -> Icons.Default.TurnRight
        "GIRA_IZQUIERDA" -> Icons.Default.TurnLeft
        "CONTINUA_RECTO" -> Icons.Default.Navigation
        "MEDIA_VUELTA" -> Icons.Default.Refresh
        "DESTINO" -> Icons.Default.GpsFixed
        else -> Icons.Default.ArrowForward
    }
}
