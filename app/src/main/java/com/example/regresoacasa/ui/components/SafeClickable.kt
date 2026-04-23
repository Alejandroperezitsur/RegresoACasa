package com.example.regresoacasa.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun Modifier.safeClickable(
    debounceTime: Long = 300L,
    onClick: () -> Unit
): Modifier {
    val scope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current
    
    return this.then(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            if (isPreview) {
                onClick()
            } else {
                scope.launch {
                    // Debounce: ignore clicks within debounceTime
                    delay(debounceTime)
                    onClick()
                }
            }
        }
    )
}

private fun Modifier.clickable(
    interactionSource: MutableInteractionSource,
    indication: androidx.compose.foundation.Indication?,
    onClick: () -> Unit
): Modifier {
    return androidx.compose.foundation.clickable(
        interactionSource = interactionSource,
        indication = indication,
        onClick = onClick
    )
}
