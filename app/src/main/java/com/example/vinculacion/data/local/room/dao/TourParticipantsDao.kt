package com.example.vinculacion.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.vinculacion.data.local.room.entities.TourParticipantEntity
import com.example.vinculacion.data.local.room.entities.TourParticipantStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TourParticipantsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(participant: TourParticipantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(participants: List<TourParticipantEntity>)

    @Query("SELECT * FROM tour_participantes WHERE tour_id = :tourId")
    fun observeByTour(tourId: String): Flow<List<TourParticipantEntity>>

    @Query("SELECT * FROM tour_participantes")
    fun observeAll(): Flow<List<TourParticipantEntity>>

    @Query("SELECT * FROM tour_participantes WHERE tour_id = :tourId")
    suspend fun getByTour(tourId: String): List<TourParticipantEntity>

    @Query("SELECT * FROM tour_participantes WHERE usuario_id = :userId")
    fun observeByUser(userId: String): Flow<List<TourParticipantEntity>>

    @Query("SELECT * FROM tour_participantes WHERE tour_id = :tourId AND usuario_id = :userId LIMIT 1")
    suspend fun getParticipant(tourId: String, userId: String): TourParticipantEntity?

    @Query("UPDATE tour_participantes SET estado = :status, procesado_en = :processedAt, actualizado_en = :updatedAt WHERE tour_id = :tourId AND usuario_id = :userId")
    suspend fun updateStatus(
        tourId: String,
        userId: String,
        status: TourParticipantStatus,
        processedAt: Long? = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun delete(participant: TourParticipantEntity)

    @Query("DELETE FROM tour_participantes")
    suspend fun clear()

    @Query("DELETE FROM tour_participantes WHERE tour_id = :tourId")
    suspend fun deleteByTour(tourId: String)

    @androidx.room.Transaction
    suspend fun replaceAll(participants: List<TourParticipantEntity>) {
        clear()
        if (participants.isNotEmpty()) upsert(participants)
    }

    @androidx.room.Transaction
    suspend fun replaceForTour(tourId: String, participants: List<TourParticipantEntity>) {
        deleteByTour(tourId)
        if (participants.isNotEmpty()) upsert(participants)
    }
}
