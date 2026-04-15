package com.example.regresoacasa.data.local

import androidx.room.*
import com.example.regresoacasa.data.local.entity.CachedRouteEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para caché de rutas
 */
@Dao
interface CachedRouteDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: CachedRouteEntity)
    
    @Query("SELECT * FROM cached_routes WHERE key = :key")
    suspend fun getRouteByKey(key: String): CachedRouteEntity?
    
    @Query("SELECT * FROM cached_routes WHERE key = :key")
    fun getRouteByKeyFlow(key: String): Flow<CachedRouteEntity?>
    
    @Query("SELECT * FROM cached_routes ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRoutes(limit: Int = 10): List<CachedRouteEntity>
    
    @Query("DELETE FROM cached_routes WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
    
    @Query("DELETE FROM cached_routes WHERE key = :key")
    suspend fun deleteRoute(key: String)
    
    @Query("DELETE FROM cached_routes")
    suspend fun deleteAll()
}
