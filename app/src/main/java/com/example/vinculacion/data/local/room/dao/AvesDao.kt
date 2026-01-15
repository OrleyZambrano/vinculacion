package com.example.vinculacion.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.vinculacion.data.local.room.entities.AveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AvesDao {

    @Query("SELECT * FROM aves ORDER BY titulo")
    fun observeAves(): Flow<List<AveEntity>>

    @Query("SELECT * FROM aves ORDER BY titulo")
    suspend fun getAves(): List<AveEntity>

    @Query("SELECT * FROM aves ORDER BY popularidad DESC, titulo LIMIT :limit")
    fun observeTopAves(limit: Int): Flow<List<AveEntity>>

    @Query("SELECT * FROM aves WHERE id = :id")
    suspend fun getAveById(id: Long): AveEntity?

    @Query("SELECT * FROM aves WHERE nombre_cientifico = :scientificName LIMIT 1")
    suspend fun getAveByScientificName(scientificName: String): AveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(aves: List<AveEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ave: AveEntity)

    @Query("DELETE FROM aves")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(aves: List<AveEntity>) {
        clear()
        if (aves.isNotEmpty()) {
            upsertAll(aves)
        }
    }

    @Query(
        "SELECT * FROM aves WHERE (:categoriaId IS NULL OR categoria_id = :categoriaId) AND (titulo LIKE '%' || :query || '%' OR nombre_comun LIKE '%' || :query || '%' OR nombre_cientifico LIKE '%' || :query || '%') ORDER BY popularidad DESC, titulo"
    )
    fun searchAves(query: String, categoriaId: String?): Flow<List<AveEntity>>

    @Query("UPDATE aves SET popularidad = popularidad + :increment WHERE id = :aveId")
    suspend fun increasePopularity(aveId: Long, increment: Int = 1)
}
