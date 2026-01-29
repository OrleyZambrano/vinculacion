package com.example.vinculacion.data.local.room.mappers

import com.example.vinculacion.data.local.room.entities.RouteEntity
import com.example.vinculacion.data.model.GuideRoute

fun RouteEntity.toDomain(): GuideRoute = GuideRoute(
    id = id,
    title = titulo,
    geoJson = geoJson,
    guideId = guideId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun GuideRoute.toEntity(): RouteEntity = RouteEntity(
    id = id,
    titulo = title,
    geoJson = geoJson,
    guideId = guideId,
    createdAt = createdAt,
    updatedAt = updatedAt
)
