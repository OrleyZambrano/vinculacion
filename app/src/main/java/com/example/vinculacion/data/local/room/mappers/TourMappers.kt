package com.example.vinculacion.data.local.room.mappers

import com.example.vinculacion.data.local.room.entities.TourEntity
import com.example.vinculacion.data.model.Tour

fun TourEntity.toDomain(): Tour = Tour(
    id = id,
    title = titulo,
    description = descripcion,
    guideId = guideId,
    guideName = guideName,
    guidePhone = guidePhone,
    guideEmail = guideEmail,
    coverImageUrl = coverImageUrl,
    difficulty = difficulty,
    status = status,
    startTimeEpoch = startTimeEpoch,
    endTimeEpoch = endTimeEpoch,
    meetingPointLat = meetingPointLat,
    meetingPointLng = meetingPointLng,
    meetingPoint = meetingPoint,
    capacity = capacity,
    suggestedPrice = suggestedPrice,
    routeId = routeId,
    routeGeoJson = routeGeoJson,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isLocalOnly = isLocalOnly
)

fun Tour.toEntity(): TourEntity = TourEntity(
    id = id,
    titulo = title,
    descripcion = description,
    guideId = guideId,
    guideName = guideName,
    guidePhone = guidePhone,
    guideEmail = guideEmail,
    coverImageUrl = coverImageUrl,
    difficulty = difficulty,
    status = status,
    startTimeEpoch = startTimeEpoch,
    endTimeEpoch = endTimeEpoch,
    meetingPoint = meetingPoint,
    meetingPointLat = meetingPointLat,
    meetingPointLng = meetingPointLng,
    capacity = capacity,
    suggestedPrice = suggestedPrice,
    routeId = routeId,
    routeGeoJson = routeGeoJson,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isLocalOnly = isLocalOnly
)
