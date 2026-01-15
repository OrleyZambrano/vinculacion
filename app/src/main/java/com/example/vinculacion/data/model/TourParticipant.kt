package com.example.vinculacion.data.model

import com.example.vinculacion.data.local.room.entities.TourParticipantStatus

/**
 * Representa a un participante asociado a un tour.
 */
data class TourParticipant(
    val id: String = "",
    val tourId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val userEmail: String = "",
    val status: TourParticipantStatus = TourParticipantStatus.PENDING,
    val requestedAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null,
    val notes: String = ""
)
