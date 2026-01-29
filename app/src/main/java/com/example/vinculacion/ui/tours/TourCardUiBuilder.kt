package com.example.vinculacion.ui.tours

import com.example.vinculacion.data.local.room.entities.TourParticipantStatus
import com.example.vinculacion.data.model.Tour

/**
 * Builds a TourCardUi from tour data and user context
 */
fun buildTourCardUi(
    tour: Tour,
    isGuide: Boolean,
    joinStatus: TourParticipantStatus?,
    canCancelJoin: Boolean,
    currentUserId: String
): TourCardUi {
    val canRequestJoin = !isGuide && 
        joinStatus == null && 
        tour.status == com.example.vinculacion.data.local.room.entities.TourStatus.PUBLISHED &&
        tour.guideId != currentUserId

    return TourCardUi(
        tour = tour,
        joinStatus = joinStatus,
        requiresAuthentication = false,
        canRequestJoin = canRequestJoin,
        canCancelJoin = canCancelJoin,
        isGuide = isGuide,
        approvedCount = 0,
        capacityRemaining = tour.capacity
    )
}