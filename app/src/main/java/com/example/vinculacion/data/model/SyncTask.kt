package com.example.vinculacion.data.model

import com.example.vinculacion.data.local.room.entities.SyncTaskState

/**
 * Representa una tarea pendiente para sincronización diferida.
 */
data class SyncTask(
    val id: Long,
    val payloadType: String,
    val payloadId: String?,
    val payloadJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attemptCount: Int,
    val lastAttemptAt: Long?,
    val state: SyncTaskState
)
