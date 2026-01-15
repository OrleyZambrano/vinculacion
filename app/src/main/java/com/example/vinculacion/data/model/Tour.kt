package com.example.vinculacion.data.model

import com.example.vinculacion.data.local.room.entities.TourStatus

/**
 * Representa la información principal de un tour guiado.
 */
data class Tour(
    val id: String,
    val title: String,
    val description: String?,
    val guideId: String,
    val guideName: String?,
    val guidePhone: String?, // WhatsApp del guía
    val guideEmail: String?,
    val coverImageUrl: String?,
    val difficulty: String?,
    val status: TourStatus,
    val startTimeEpoch: Long,
    val endTimeEpoch: Long?,
    val meetingPoint: String?, // Punto de encuentro descripción
    val meetingPointLat: Double?,
    val meetingPointLng: Double?,
    val capacity: Int?,
    val suggestedPrice: Double?,
    val routeGeoJson: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isLocalOnly: Boolean
)
