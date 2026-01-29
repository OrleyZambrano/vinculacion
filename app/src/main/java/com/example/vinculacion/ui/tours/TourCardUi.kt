package com.example.vinculacion.ui.tours

import com.example.vinculacion.data.local.room.entities.TourParticipantStatus
import com.example.vinculacion.data.model.Tour

/**
 * Representa la información mostrada para cada tarjeta de tour en la UI.
 */
data class TourCardUi(
    val tour: Tour,
    val joinStatus: TourParticipantStatus?,
    val requiresAuthentication: Boolean,
    val canRequestJoin: Boolean,
    val canCancelJoin: Boolean,
    val isGuide: Boolean,
    val approvedCount: Int,
    val capacityRemaining: Int?
) {
    val hasPendingRequest: Boolean = joinStatus == TourParticipantStatus.PENDING
}
