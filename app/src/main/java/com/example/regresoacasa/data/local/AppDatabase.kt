package com.example.regresoacasa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.regresoacasa.data.local.entity.LugarFavoritoEntity

@Database(
    entities = [LugarFavoritoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lugarFavoritoDao(): LugarFavoritoDao
}
