package com.example.regresoacasa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.regresoacasa.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>
    
    @Query("SELECT * FROM search_history ORDER BY searchCount DESC, timestamp DESC LIMIT 10")
    fun getMostFrequentSearches(): Flow<List<SearchHistoryEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(search: SearchHistoryEntity)
    
    @Query("UPDATE search_history SET searchCount = searchCount + 1, timestamp = :timestamp WHERE query = :query")
    suspend fun incrementSearchCount(query: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM search_history WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("DELETE FROM search_history")
    suspend fun clearAll()
    
    @Query("SELECT EXISTS(SELECT 1 FROM search_history WHERE query = :query)")
    suspend fun exists(query: String): Boolean
}
