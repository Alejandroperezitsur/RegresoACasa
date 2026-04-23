package com.example.regresoacasa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.regresoacasa.data.local.entity.AlertDeliveryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertDeliveryEntity)

    @Update
    suspend fun updateAlert(alert: AlertDeliveryEntity)

    @Query("SELECT * FROM alert_deliveries WHERE alertId = :alertId")
    suspend fun getAlertById(alertId: String): AlertDeliveryEntity?

    @Query("SELECT * FROM alert_deliveries WHERE tripId = :tripId ORDER BY sentTimestamp DESC")
    fun getAlertsByTrip(tripId: String): Flow<List<AlertDeliveryEntity>>

    @Query("SELECT * FROM alert_deliveries WHERE sendStatus = 'PENDING' ORDER BY sentTimestamp ASC")
    suspend fun getPendingAlerts(): List<AlertDeliveryEntity>

    @Query("SELECT * FROM alert_deliveries WHERE sendStatus = 'SENT' AND deliveryStatus = 'UNKNOWN' AND sentTimestamp < :threshold ORDER BY sentTimestamp ASC")
    suspend fun getUncertainDeliveries(threshold: Long): List<AlertDeliveryEntity>

    @Query("DELETE FROM alert_deliveries WHERE sentTimestamp < :beforeTimestamp")
    suspend fun deleteOldAlerts(beforeTimestamp: Long)
}
