package com.example.regresoacasa.data.local

import androidx.room.*
import com.example.regresoacasa.data.local.entity.CachedRouteEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para caché de rutas con TTL de 24h
 */
@Dao
interface CachedRouteDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: CachedRouteEntity)
    
    @Query("SELECT * FROM cached_routes WHERE key = :key AND timestamp > :ttl")
    suspend fun getRouteByKey(key: String, ttl: Long): CachedRouteEntity?
    
    @Query("SELECT * FROM cached_routes WHERE key = :key AND timestamp > :ttl")
    fun getRouteByKeyFlow(key: String, ttl: Long): Flow<CachedRouteEntity?>
    
    @Query("SELECT * FROM cached_routes WHERE timestamp > :ttl ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRoutes(ttl: Long, limit: Int = 10): List<CachedRouteEntity>
    
    @Query("DELETE FROM cached_routes WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
    
    @Query("DELETE FROM cached_routes WHERE key = :key")
    suspend fun deleteRoute(key: String)
    
    @Query("DELETE FROM cached_routes")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM cached_routes WHERE " +
           "ABS(startLat - :lat) < 0.001 AND " +
           "ABS(startLng - :lng) < 0.001 AND " +
           "ABS(endLat - :endLat) < 0.001 AND " +
           "ABS(endLng - :endLng) < 0.001 AND " +
           "timestamp > :ttl LIMIT 1")
    suspend fun findSimilarRoute(
        lat: Double, 
        lng: Double, 
        endLat: Double, 
        endLng: Double, 
        ttl: Long
    ): CachedRouteEntity?
}
