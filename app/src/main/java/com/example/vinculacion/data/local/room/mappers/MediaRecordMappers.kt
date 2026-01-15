package com.example.vinculacion.data.local.room.mappers

import com.example.vinculacion.data.local.room.entities.MediaRecordEntity
import com.example.vinculacion.data.model.MediaRecord
import com.example.vinculacion.data.model.MediaRecordDraft

fun MediaRecordEntity.toDomain(): MediaRecord = MediaRecord(
    id = id,
    aveId = aveId,
    type = type,
    localPath = localPath,
    remoteUrl = remoteUrl,
    thumbnailPath = thumbnailPath,
    confidence = confidence,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    capturedAt = capturedAt,
    registeredAt = registeredAt,
    createdByUserId = createdByUserId,
    syncStatus = syncStatus,
    payloadJson = payloadJson
)

fun MediaRecord.toEntity(): MediaRecordEntity = MediaRecordEntity(
    id = id,
    aveId = aveId,
    type = type,
    localPath = localPath,
    remoteUrl = remoteUrl,
    thumbnailPath = thumbnailPath,
    confidence = confidence,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    capturedAt = capturedAt,
    registeredAt = registeredAt,
    createdByUserId = createdByUserId,
    syncStatus = syncStatus,
    payloadJson = payloadJson
)

fun MediaRecordDraft.toEntity(): MediaRecordEntity = MediaRecordEntity(
    aveId = aveId,
    type = type,
    localPath = localPath,
    remoteUrl = remoteUrl,
    thumbnailPath = thumbnailPath,
    confidence = confidence,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    capturedAt = capturedAt,
    createdByUserId = createdByUserId,
    registeredAt = System.currentTimeMillis(),
    payloadJson = payloadJson
)
