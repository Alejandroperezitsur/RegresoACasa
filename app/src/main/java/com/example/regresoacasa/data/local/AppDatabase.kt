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
import com.example.regresoacasa.data.local.entity.EmergencyAlertEntity

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // No schema changes needed, just TTL logic in queries
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS emergency_alerts (
                id TEXT PRIMARY KEY NOT NULL,
                reason TEXT NOT NULL,
                contacts TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                timestamp INTEGER NOT NULL,
                deliveryMethod TEXT NOT NULL,
                status TEXT NOT NULL
            )
        """)
    }
}

@Database(
    entities = [LugarFavoritoEntity::class, CachedRouteEntity::class, SearchHistoryEntity::class, EmergencyContactEntity::class, TripEntity::class, AlertDeliveryEntity::class, EmergencyAlertEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lugarFavoritoDao(): LugarFavoritoDao
    abstract fun cachedRouteDao(): CachedRouteDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun tripDao(): TripDao
    abstract fun alertDeliveryDao(): AlertDeliveryDao
    abstract fun emergencyAlertDao(): EmergencyAlertDao
}
