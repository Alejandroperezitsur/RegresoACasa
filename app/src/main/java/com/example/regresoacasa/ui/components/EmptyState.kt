package com.example.regresoacasa.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

@Composable
fun NoInternetEmptyState(onRetry: () -> Unit) {
    EmptyState(
        icon = Icons.Default.WifiOff,
        title = "Sin conexión a internet",
        message = "Verifica tu conexión y vuelve a intentar",
        actionText = "Reintentar",
        onAction = onRetry
    )
}

@Composable
fun NoResultsEmptyState() {
    EmptyState(
        icon = Icons.Default.SearchOff,
        title = "Sin resultados",
        message = "Intenta con una búsqueda diferente"
    )
}

@Composable
fun NoLocationEmptyState(onOpenSettings: () -> Unit) {
    EmptyState(
        icon = Icons.Default.LocationOff,
        title = "Ubicación no disponible",
        message = "Activa el GPS para continuar",
        actionText = "Abrir configuración",
        onAction = onOpenSettings
    )
}
