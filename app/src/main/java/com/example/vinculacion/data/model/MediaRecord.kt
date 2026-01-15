package com.example.vinculacion.data.model

import com.example.vinculacion.data.local.room.entities.MediaRecordType
import com.example.vinculacion.data.local.room.entities.MediaSyncStatus

/**
 * Representa un registro multimedia de foto o audio para un avistamiento.
 */
data class MediaRecord(
    val id: Long,
    val aveId: Long?,
    val type: MediaRecordType,
    val localPath: String?,
    val remoteUrl: String?,
    val thumbnailPath: String?,
    val confidence: Float?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val capturedAt: Long,
    val registeredAt: Long,
    val createdByUserId: String?,
    val syncStatus: MediaSyncStatus,
    val payloadJson: String?
)
