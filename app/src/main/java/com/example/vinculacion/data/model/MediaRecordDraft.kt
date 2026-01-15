package com.example.vinculacion.data.model

import com.example.vinculacion.data.local.room.entities.MediaRecordType

/**
 * Representa la información mínima necesaria para registrar un nuevo avistamiento.
 */
data class MediaRecordDraft(
    val aveId: Long?,
    val type: MediaRecordType,
    val localPath: String?,
    val remoteUrl: String? = null,
    val thumbnailPath: String? = null,
    val confidence: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val capturedAt: Long,
    val createdByUserId: String? = null,
    val payloadJson: String? = null
)
