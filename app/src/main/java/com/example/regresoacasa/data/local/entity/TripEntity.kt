package com.example.regresoacasa.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity para historial de viajes de seguridad
 * Persistidos en Room para análisis offline
 */
@Entity(tableName = "safety_trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val destinationAddress: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val startLat: Double,
    val startLng: Double,
    val expectedDurationMinutes: Int,
    val actualDurationMinutes: Int? = null,
    val status: TripStatus,
    val alertTriggered: Boolean = false,
    val alertLevel: String? = null,
    val emergencyContactsNotified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TripStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
    EMERGENCY
}
