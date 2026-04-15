package com.example.regresoacasa.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"], unique = true)]
)
data class SearchHistoryEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val query: String,
    val displayName: String,
    val latitud: Double,
    val longitud: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val searchCount: Int = 1
)
