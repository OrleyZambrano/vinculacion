package com.example.vinculacion.data.repository

import android.content.Context
import com.example.vinculacion.data.local.room.VinculacionDatabase
import com.example.vinculacion.data.local.room.entities.MediaSyncStatus
import com.example.vinculacion.data.local.room.entities.MediaRecordEntity
import com.example.vinculacion.data.local.room.entities.MediaRecordType
import com.example.vinculacion.data.local.room.mappers.toDomain
import com.example.vinculacion.data.local.room.mappers.toEntity
import com.example.vinculacion.data.model.MediaRecord
import com.example.vinculacion.data.model.MediaRecordDraft
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MediaRepository(context: Context) {

    private val appContext = context.applicationContext
    private val database = VinculacionDatabase.getInstance(appContext)
    private val mediaDao = database.mediaRecordsDao()
    private val firestore = FirebaseFirestore.getInstance()
    private val recordsCollection = firestore.collection("media_records")

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

    suspend fun pushRecordToRemote(record: MediaRecord) = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "id" to record.id,
            "aveId" to record.aveId,
            "type" to record.type.name,
            "latitude" to record.latitude,
            "longitude" to record.longitude,
            "altitude" to record.altitude,
            "confidence" to record.confidence,
            "capturedAt" to record.capturedAt,
            "registeredAt" to record.registeredAt,
            "createdByUserId" to record.createdByUserId,
            "updatedAt" to System.currentTimeMillis()
        )
        recordsCollection.document(record.id.toString()).set(payload).await()
    }

    suspend fun syncFromRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val snapshot = recordsCollection.get().await()
            val entities = snapshot.documents.mapNotNull { it.toMediaRecordEntity() }
            if (entities.isNotEmpty()) {
                mediaDao.upsert(entities)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRemoteWithLocation(): List<MediaRecord> = withContext(Dispatchers.IO) {
        val snapshot = recordsCollection.get().await()
        snapshot.documents
            .mapNotNull { it.toMediaRecordEntity() }
            .map { it.toDomain() }
            .filter { it.latitude != null && it.longitude != null }
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

    private fun DocumentSnapshot.toMediaRecordEntity(): MediaRecordEntity? {
        val id = getLong("id") ?: id.toLongOrNull() ?: return null
        val typeString = getString("type") ?: return null
        val type = runCatching { MediaRecordType.valueOf(typeString) }.getOrNull() ?: return null
        val capturedAt = getLong("capturedAt") ?: System.currentTimeMillis()
        val registeredAt = getLong("registeredAt") ?: capturedAt

        return MediaRecordEntity(
            id = id,
            aveId = getLong("aveId"),
            type = type,
            localPath = null,
            remoteUrl = getString("remoteUrl"),
            thumbnailPath = null,
            confidence = getDouble("confidence")?.toFloat(),
            latitude = readDouble("latitude"),
            longitude = readDouble("longitude"),
            altitude = readDouble("altitude"),
            capturedAt = capturedAt,
            registeredAt = registeredAt,
            createdByUserId = getString("createdByUserId"),
            syncStatus = MediaSyncStatus.SYNCED,
            payloadJson = null
        )
    }

    private fun DocumentSnapshot.readDouble(field: String): Double? {
        return when (val value = get(field)) {
            is Double -> value
            is Long -> value.toDouble()
            else -> null
        }
    }
}
