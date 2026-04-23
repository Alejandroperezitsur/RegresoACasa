package com.example.regresoacasa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

data class SpeedDialItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun SpeedDial(
    items: List<SpeedDialItem>,
    modifier: Modifier = Modifier,
    mainFabColor: Color = Color(0xFF1565C0),
    itemFabColor: Color = Color.White
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 45f else 0f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Speed dial items (expanded state)
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items.forEach { item ->
                    SmallFloatingActionButton(
                        onClick = {
                            item.onClick()
                            expanded = false
                        },
                        containerColor = itemFabColor,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = Color(0xFF1565C0)
                        )
                    }
                }
            }
        }

        // Main FAB
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = mainFabColor,
            modifier = Modifier.size(56.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (expanded) "Cerrar" else "Más opciones",
                tint = Color.White,
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }
    }
}
