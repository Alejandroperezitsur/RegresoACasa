package com.example.regresoacasa.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_alerts")
data class EmergencyAlertEntity(
    @PrimaryKey
    val id: String,
    val reason: String,
    val contacts: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val deliveryMethod: String,
    val status: String
)
