package com.example.regresoacasa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.regresoacasa.data.local.entity.EmergencyAlertEntity

@Dao
interface EmergencyAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: EmergencyAlertEntity)
    
    @Query("SELECT * FROM emergency_alerts ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentAlerts(): List<EmergencyAlertEntity>
    
    @Query("SELECT * FROM emergency_alerts WHERE timestamp > :since ORDER BY timestamp DESC")
    suspend fun getAlertsSince(since: Long): List<EmergencyAlertEntity>
    
    @Query("DELETE FROM emergency_alerts WHERE timestamp < :before")
    suspend fun deleteOldAlerts(before: Long)
}
