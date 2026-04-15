package com.example.regresoacasa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.regresoacasa.data.local.entity.CachedRouteEntity
import com.example.regresoacasa.data.local.entity.LugarFavoritoEntity
import com.example.regresoacasa.data.local.entity.SearchHistoryEntity

@Database(
    entities = [LugarFavoritoEntity::class, CachedRouteEntity::class, SearchHistoryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lugarFavoritoDao(): LugarFavoritoDao
    abstract fun cachedRouteDao(): CachedRouteDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
