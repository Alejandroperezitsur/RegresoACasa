package com.example.regresoacasa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.regresoacasa.data.local.entity.CachedRouteEntity
import com.example.regresoacasa.data.local.entity.LugarFavoritoEntity
import com.example.regresoacasa.data.local.entity.SearchHistoryEntity
import com.example.regresoacasa.data.local.entity.EmergencyContactEntity
import com.example.regresoacasa.data.local.entity.TripEntity

@Database(
    entities = [LugarFavoritoEntity::class, CachedRouteEntity::class, SearchHistoryEntity::class, EmergencyContactEntity::class, TripEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lugarFavoritoDao(): LugarFavoritoDao
    abstract fun cachedRouteDao(): CachedRouteDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun tripDao(): TripDao
}
