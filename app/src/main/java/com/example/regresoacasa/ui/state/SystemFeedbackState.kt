package com.example.regresoacasa.ui.state

/**
 * Estado del sistema de feedback háptico
 * Usado para comunicar disponibilidad de vibraciones a la UI
 */
sealed class SystemFeedbackState {
    data object Normal : SystemFeedbackState()
    data object HapticsUnavailable : SystemFeedbackState()
}
