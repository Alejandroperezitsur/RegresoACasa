package com.example.regresoacasa.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lugares_favoritos")
data class LugarFavoritoEntity(
    @PrimaryKey
    val id: String,
    val nombre: String,
    val direccion: String,
    val latitud: Double,
    val longitud: Double,
    val tipo: String // CASA, TRABAJO, OTRO
)
