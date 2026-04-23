package com.example.regresoacasa.data.safety

/**
 * Modelo de dominio para contacto de emergencia
 */
data class EmergencyContact(
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val relationship: String = "",
    val isPrimary: Boolean = false
)
