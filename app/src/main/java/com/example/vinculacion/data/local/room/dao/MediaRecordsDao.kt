package com.example.vinculacion.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.vinculacion.data.local.room.entities.MediaRecordEntity
import com.example.vinculacion.data.local.room.entities.MediaRecordType
import com.example.vinculacion.data.local.room.entities.MediaSyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaRecordsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: MediaRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(records: List<MediaRecordEntity>)

    @Query("SELECT * FROM media_registros WHERE id = :id")
    suspend fun getById(id: Long): MediaRecordEntity?

    @Query("SELECT * FROM media_registros WHERE ave_id = :aveId ORDER BY capturado_en DESC")
    fun observeByAve(aveId: Long): Flow<List<MediaRecordEntity>>

    @Query("SELECT * FROM media_registros WHERE tipo = :type ORDER BY capturado_en DESC")
    fun observeByType(type: MediaRecordType): Flow<List<MediaRecordEntity>>

    @Query("SELECT * FROM media_registros WHERE latitude IS NOT NULL AND longitude IS NOT NULL")
    fun observeWithLocation(): Flow<List<MediaRecordEntity>>

    @Query("SELECT * FROM media_registros WHERE latitude IS NOT NULL AND longitude IS NOT NULL")
    suspend fun getWithLocation(): List<MediaRecordEntity>

    @Query("SELECT * FROM media_registros WHERE sync_status = :status ORDER BY registrado_en ASC")
    suspend fun getPendingByStatus(status: MediaSyncStatus = MediaSyncStatus.PENDING): List<MediaRecordEntity>

    @Query("UPDATE media_registros SET sync_status = :status, registrado_en = :updatedAt, ruta_remota = COALESCE(:remoteUrl, ruta_remota), payload_json = :payloadJson WHERE id = :id")
    suspend fun updateSyncState(
        id: Long,
        status: MediaSyncStatus,
        updatedAt: Long = System.currentTimeMillis(),
        remoteUrl: String? = null,
        payloadJson: String? = null
    )

    @Delete
    suspend fun delete(record: MediaRecordEntity)

    @Query("DELETE FROM media_registros WHERE sync_status = :status")
    suspend fun deleteBySyncStatus(status: MediaSyncStatus)

    @Query("SELECT COUNT(*) FROM media_registros")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM media_registros WHERE ave_id = :aveId")
    suspend fun countByAve(aveId: Long): Int
}
