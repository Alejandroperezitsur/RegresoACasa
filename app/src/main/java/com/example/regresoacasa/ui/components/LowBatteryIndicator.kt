package com.example.regresoacasa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
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

@Composable
fun LowBatteryIndicator(
    batteryLevel: Int,
    isLowPowerMode: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isLowPowerMode,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Card(
            modifier = modifier,
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50).copy(alpha = 0.9f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BatterySaver,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                
                Spacer(modifier = Modifier.width(6.dp))
                
                Text(
                    text = "♻️ $batteryLevel%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
    }
}
