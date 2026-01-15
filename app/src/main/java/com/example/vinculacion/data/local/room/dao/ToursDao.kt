package com.example.vinculacion.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.vinculacion.data.local.room.entities.TourEntity
import com.example.vinculacion.data.local.room.entities.TourStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ToursDao {

    @Query("SELECT * FROM tours ORDER BY inicio_epoch")
    fun observeTours(): Flow<List<TourEntity>>

    @Query("SELECT * FROM tours WHERE id = :id")
    suspend fun getTour(id: String): TourEntity?

    @Query("SELECT * FROM tours WHERE estado IN (:statuses) ORDER BY inicio_epoch")
    fun observeByStatus(statuses: List<TourStatus>): Flow<List<TourEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tour: TourEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tours: List<TourEntity>)

    @Query("DELETE FROM tours WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tours")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(tours: List<TourEntity>) {
        clear()
        if (tours.isNotEmpty()) upsert(tours)
    }
}
