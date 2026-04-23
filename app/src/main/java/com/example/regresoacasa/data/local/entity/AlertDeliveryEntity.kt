package com.example.regresoacasa.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_deliveries")
data class AlertDeliveryEntity(
    @PrimaryKey
    val alertId: String,
    val contactPhone: String,
    val message: String,
    val sendStatus: SendStatus,
    val deliveryStatus: DeliveryStatus,
    val retryCount: Int,
    val sentTimestamp: Long,
    val deliveredTimestamp: Long?,
    val locationLat: Double?,
    val locationLng: Double?,
    val batteryLevel: Int?,
    val tripId: String?
) {
    enum class SendStatus {
        PENDING,
        SENT,
        FAILED
    }

    enum class DeliveryStatus {
        UNKNOWN,
        DELIVERED,
        FAILED,
        UNCERTAIN
    }
}
