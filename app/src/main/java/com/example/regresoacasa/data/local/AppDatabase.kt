package com.example.regresoacasa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.regresoacasa.data.local.entity.CachedRouteEntity
import com.example.regresoacasa.data.local.entity.LugarFavoritoEntity
import com.example.regresoacasa.data.local.entity.SearchHistoryEntity
import com.example.regresoacasa.data.local.entity.EmergencyContactEntity
import com.example.regresoacasa.data.local.entity.TripEntity
import com.example.regresoacasa.data.local.entity.AlertDeliveryEntity

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // No schema changes needed, just TTL logic in queries
    }
}

@Database(
    entities = [LugarFavoritoEntity::class, CachedRouteEntity::class, SearchHistoryEntity::class, EmergencyContactEntity::class, TripEntity::class, AlertDeliveryEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lugarFavoritoDao(): LugarFavoritoDao
    abstract fun cachedRouteDao(): CachedRouteDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun tripDao(): TripDao
    abstract fun alertDeliveryDao(): AlertDeliveryDao
}
