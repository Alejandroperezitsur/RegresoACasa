package com.example.regresoacasa.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity para contactos de emergencia
 * Persistidos en Room para uso offline
 */
@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val relationship: String = "",
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
