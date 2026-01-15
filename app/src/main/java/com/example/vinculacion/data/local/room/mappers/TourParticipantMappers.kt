package com.example.vinculacion.data.local.room.mappers

import com.example.vinculacion.data.local.room.entities.TourParticipantEntity
import com.example.vinculacion.data.model.TourParticipant

fun TourParticipantEntity.toDomain(): TourParticipant = TourParticipant(
    id = id,
    tourId = tourId,
    userId = userId,
    userName = userName ?: "",
    userPhone = userPhone ?: "",
    userEmail = userEmail ?: "",
    status = status,
    requestedAt = requestedAt,
    processedAt = processedAt,
    notes = notes ?: ""
)

fun TourParticipant.toEntity(): TourParticipantEntity = TourParticipantEntity(
    id = id,
    tourId = tourId,
    userId = userId,
    userName = userName,
    userPhone = userPhone,
    userEmail = userEmail,
    status = status,
    requestedAt = requestedAt,
    processedAt = processedAt,
    updatedAt = processedAt ?: requestedAt,
    notes = notes
)
