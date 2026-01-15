package com.example.vinculacion.data.repository

import android.content.Context
import com.example.vinculacion.data.local.room.VinculacionDatabase
import com.example.vinculacion.data.local.room.entities.MediaSyncStatus
import com.example.vinculacion.data.local.room.mappers.toDomain
import com.example.vinculacion.data.local.room.mappers.toEntity
import com.example.vinculacion.data.model.MediaRecord
import com.example.vinculacion.data.model.MediaRecordDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MediaRepository(context: Context) {

    private val appContext = context.applicationContext
    private val database = VinculacionDatabase.getInstance(appContext)
    private val mediaDao = database.mediaRecordsDao()

    fun observeByAve(aveId: Long): Flow<List<MediaRecord>> =
        mediaDao.observeByAve(aveId).map { entities -> entities.map { entity -> entity.toDomain() } }

    fun observeWithLocation(): Flow<List<MediaRecord>> =
        mediaDao.observeWithLocation().map { list -> list.map { it.toDomain() } }

    suspend fun saveDraft(draft: MediaRecordDraft): MediaRecord = withContext(Dispatchers.IO) {
        val entity = draft.toEntity()
        val id = mediaDao.upsert(entity)
        val record = mediaDao.getById(id)?.toDomain() ?: entity.copy(id = id).toDomain()
        record
    }

    suspend fun updateSyncStatus(id: Long, status: MediaSyncStatus, remoteUrl: String? = null, payloadJson: String? = null) =
        withContext(Dispatchers.IO) {
            mediaDao.updateSyncState(id, status, remoteUrl = remoteUrl, payloadJson = payloadJson)
        }

    suspend fun getPendingSync(): List<MediaRecord> = withContext(Dispatchers.IO) {
        mediaDao.getPendingByStatus(MediaSyncStatus.PENDING).map { it.toDomain() }
    }

    suspend fun getWithLocation(): List<MediaRecord> = withContext(Dispatchers.IO) {
        mediaDao.getWithLocation().map { it.toDomain() }
    }

    suspend fun delete(record: MediaRecord) {
        withContext(Dispatchers.IO) {
            mediaDao.delete(record.toEntity())
        }
    }
}
