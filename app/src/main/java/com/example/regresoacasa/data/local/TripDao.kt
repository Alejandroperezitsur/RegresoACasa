package com.example.regresoacasa.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.regresoacasa.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones con viajes de seguridad
 */
@Dao
interface TripDao {
    
    @Query("SELECT * FROM safety_trips ORDER BY startTime DESC")
    fun getAllTrips(): Flow<List<TripEntity>>
    
    @Query("SELECT * FROM safety_trips WHERE tripId = :tripId LIMIT 1")
    suspend fun getTripByTripId(tripId: String): TripEntity?
    
    @Query("SELECT * FROM safety_trips WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long
    
    @Update
    suspend fun updateTrip(trip: TripEntity)
    
    @Delete
    suspend fun deleteTrip(trip: TripEntity)
    
    @Query("UPDATE safety_trips SET status = :status, endTime = :endTime WHERE tripId = :tripId")
    suspend fun updateTripStatus(tripId: String, status: String, endTime: Long)
    
    @Query("UPDATE safety_trips SET alertTriggered = 1, alertLevel = :alertLevel WHERE tripId = :tripId")
    suspend fun markAlertTriggered(tripId: String, alertLevel: String)
    
    @Query("UPDATE safety_trips SET emergencyContactsNotified = 1 WHERE tripId = :tripId")
    suspend fun markContactsNotified(tripId: String)
    
    @Query("SELECT COUNT(*) FROM safety_trips WHERE status = 'ACTIVE'")
    suspend fun getActiveTripCount(): Int
    
    @Query("DELETE FROM safety_trips WHERE endTime < :cutoffTime")
    suspend fun deleteOldTrips(cutoffTime: Long)
}
