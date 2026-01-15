package com.example.vinculacion.data.local.room.mappers

import com.example.vinculacion.data.local.room.entities.SyncTaskEntity
import com.example.vinculacion.data.model.SyncTask

fun SyncTaskEntity.toDomain(): SyncTask = SyncTask(
    id = id,
    payloadType = payloadType,
    payloadId = payloadId,
    payloadJson = payloadJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
    attemptCount = attemptCount,
    lastAttemptAt = lastAttemptAt,
    state = state
)

fun SyncTask.toEntity(): SyncTaskEntity = SyncTaskEntity(
    id = id,
    payloadType = payloadType,
    payloadId = payloadId,
    payloadJson = payloadJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
    attemptCount = attemptCount,
    lastAttemptAt = lastAttemptAt,
    state = state
)
