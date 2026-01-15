package com.example.vinculacion.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.vinculacion.data.local.room.entities.CategoriaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriasDao {

    @Query("SELECT * FROM categorias ORDER BY nombre")
    fun observeCategorias(): Flow<List<CategoriaEntity>>

    @Query("SELECT * FROM categorias ORDER BY nombre")
    suspend fun getCategorias(): List<CategoriaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoriaEntity>)

    @Query("DELETE FROM categorias")
    suspend fun clear()
}
