package com.example.regresoacasa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.regresoacasa.domain.model.LugarFavorito
import com.example.regresoacasa.ui.components.MapaView
import com.example.regresoacasa.ui.state.UiState
import com.example.regresoacasa.ui.viewmodel.NavigationViewModel

@Composable
fun MainScreen(
    viewModel: NavigationViewModel,
    onRequestPermission: () -> Unit,
    onIrACasa: () -> Unit,
    onBuscarCasa: () -> Unit,
    hasLocationPermission: Boolean
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Mapa
        MapaView(
            ubicacion = uiState.ubicacionActual,
            destino = uiState.casa,
            ruta = uiState.rutaActual?.puntos,
            isFollowingUser = uiState.navigationState.isFollowingUser,
            onFollowUserToggle = { viewModel.toggleFollowUser() }
        )

        // Header superior
        HeaderSection(
            casa = uiState.casa,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Botones flotantes
        FloatingButtons(
            tieneCasa = uiState.casa != null,
            hasLocationPermission = hasLocationPermission,
            onRequestPermission = onRequestPermission,
            onMiUbicacion = { viewModel.obtenerUbicacionUnica() },
            onBuscarCasa = onBuscarCasa,
            onIrACasa = onIrACasa,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Indicadores de carga
        if (uiState.estaCargandoUbicacion || uiState.estaCalculandoRuta) {
            LoadingOverlay(
                mensaje = when {
                    uiState.estaCalculandoRuta -> "Calculando ruta..."
                    else -> "Obteniendo ubicación..."
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Mensaje de error
        if (uiState.uiState is UiState.Error) {
            ErrorCard(
                mensaje = uiState.error ?: "Error desconocido",
                onDismiss = { viewModel.limpiarError() },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun HeaderSection(
    casa: LugarFavorito?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1565C0).copy(alpha = 0.95f),
                        Color(0xFF1565C0).copy(alpha = 0.7f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Regreso a Casa",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            if (casa != null) {
                Text(
                    text = casa.direccion,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1
                )
            } else {
                Text(
                    text = "Configura tu casa para empezar",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun FloatingButtons(
    tieneCasa: Boolean,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onMiUbicacion: () -> Unit,
    onBuscarCasa: () -> Unit,
    onIrACasa: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Botón mi ubicación
        FloatingActionButton(
            onClick = {
                if (hasLocationPermission) onMiUbicacion() else onRequestPermission()
            },
            containerColor = Color.White,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Mi ubicación",
                tint = Color(0xFF1565C0)
            )
        }

        // Botón principal: Ir a Casa o Configurar Casa
        if (tieneCasa) {
            Button(
                onClick = onIrACasa,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1565C0)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .height(56.dp)
                    .shadow(8.dp, RoundedCornerShape(28.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Ir a Casa",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        } else {
            Button(
                onClick = onBuscarCasa,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6F00)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .height(56.dp)
                    .shadow(8.dp, RoundedCornerShape(28.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Configurar Casa",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun LoadingOverlay(
    mensaje: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
                    text = mensaje,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    mensaje: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(32.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️ Atención",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC62828)
                )
            ) {
                Text("Entendido")
            }
        }
    }
}
