package com.example.regresoacasa.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.regresoacasa.data.local.entity.LugarFavoritoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LugarFavoritoDao {

    @Query("SELECT * FROM lugares_favoritos ORDER BY nombre ASC")
    fun getAllFavoritos(): Flow<List<LugarFavoritoEntity>>

    @Query("SELECT * FROM lugares_favoritos WHERE tipo = 'CASA' LIMIT 1")
    suspend fun getCasa(): LugarFavoritoEntity?

    @Query("SELECT * FROM lugares_favoritos WHERE tipo = 'TRABAJO' LIMIT 1")
    suspend fun getTrabajo(): LugarFavoritoEntity?

    @Query("SELECT * FROM lugares_favoritos WHERE id = :id")
    suspend fun getFavoritoById(id: String): LugarFavoritoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorito(favorito: LugarFavoritoEntity)

    @Delete
    suspend fun deleteFavorito(favorito: LugarFavoritoEntity)

    @Query("DELETE FROM lugares_favoritos WHERE id = :id")
    suspend fun deleteFavoritoById(id: String)
}
