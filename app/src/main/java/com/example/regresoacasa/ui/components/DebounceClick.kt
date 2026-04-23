package com.example.regresoacasa.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

fun debounceClick(
    scope: CoroutineScope,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    debounceTime: Long = 1000L
) {
    val debounceJob = Job()
    
    interactionSource.interactions
        .filter { it is PressInteraction.Release }
        .onEach {
            debounceJob.cancel()
            debounceJob.invokeOnCancellation {
                // Job was cancelled, don't execute click
            }
        }
        .launchIn(scope)
    
    interactionSource.interactions
        .filter { it is PressInteraction.Release }
        .onEach {
            delay(debounceTime)
            onClick()
        }
        .launchIn(scope)
}
