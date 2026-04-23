package com.example.regresoacasa.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.regresoacasa.data.local.entity.EmergencyContactEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones con contactos de emergencia
 */
@Dao
interface EmergencyContactDao {
    
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, createdAt ASC")
    fun getAllContacts(): Flow<List<EmergencyContactEntity>>
    
    @Query("SELECT * FROM emergency_contacts WHERE id = :id")
    suspend fun getContactById(id: Long): EmergencyContactEntity?
    
    @Query("SELECT * FROM emergency_contacts WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryContact(): EmergencyContactEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContactEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<EmergencyContactEntity>)
    
    @Update
    suspend fun updateContact(contact: EmergencyContactEntity)
    
    @Delete
    suspend fun deleteContact(contact: EmergencyContactEntity)
    
    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteContactById(id: Long)
    
    @Query("UPDATE emergency_contacts SET isPrimary = 0")
    suspend fun clearPrimaryContact()
    
    @Query("UPDATE emergency_contacts SET isPrimary = 1 WHERE id = :id")
    suspend fun setPrimaryContact(id: Long)
    
    @Query("SELECT COUNT(*) FROM emergency_contacts")
    suspend fun getContactCount(): Int
}
