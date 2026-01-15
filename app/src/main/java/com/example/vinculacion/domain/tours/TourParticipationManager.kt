package com.example.vinculacion.domain.tours

import android.content.Context
import com.example.vinculacion.data.local.room.entities.TourParticipantStatus
import com.example.vinculacion.data.model.Tour
import com.example.vinculacion.data.model.TourParticipant
import com.example.vinculacion.data.model.UserProfile
import com.example.vinculacion.data.repository.SyncRepository
import com.example.vinculacion.data.repository.ToursRepository
import org.json.JSONObject

/**
 * Centraliza las operaciones de participación en tours, incluyendo la encolación para sincronización.
 */
class TourParticipationManager(context: Context) {

    private val toursRepository = ToursRepository(context)
    private val syncRepository = SyncRepository(context)

    suspend fun requestJoin(tour: Tour, profile: UserProfile): Result<TourParticipant> {
        if (!profile.role.isAuthenticated()) {
            return Result.failure(IllegalStateException("Se requiere autenticación para solicitar un cupo"))
        }
        val participant = TourParticipant(
            id = java.util.UUID.randomUUID().toString(),
            tourId = tour.id,
            userId = profile.id,
            userName = profile.handle ?: profile.displayName,
            userPhone = profile.phone ?: "",
            userEmail = profile.email ?: "",
            status = TourParticipantStatus.PENDING,
            requestedAt = System.currentTimeMillis(),
            processedAt = null,
            notes = ""
        )
        val result = toursRepository.requestJoin(participant)
        result.fold(
            onSuccess = {
                enqueueJoinRequest(participant, tour.guideId)
            },
            onFailure = { error ->
                return Result.failure(error)
            }
        )
        return Result.success(participant)
    }

    suspend fun cancelRequest(tourId: String, profile: UserProfile): Result<Unit> {
        val current = toursRepository.getParticipant(tourId, profile.id)
            ?: return Result.failure(IllegalStateException("No existe una solicitud previa"))
        toursRepository.updateParticipantStatus(tourId, profile.id, TourParticipantStatus.CANCELLED)
        enqueueParticipantUpdate(tourId, profile.id, TourParticipantStatus.CANCELLED, profile.id)
        return Result.success(Unit)
    }

    suspend fun updateParticipantStatus(
        tour: Tour,
        participant: TourParticipant,
        status: TourParticipantStatus,
        actor: UserProfile
    ): Result<Unit> {
        if (!actor.role.canManageTours() || actor.id != tour.guideId) {
            return Result.failure(IllegalStateException("Solo la guía asignada puede gestionar solicitudes"))
        }
        toursRepository.updateParticipantStatus(tour.id, participant.userId, status)
        enqueueParticipantUpdate(tour.id, participant.userId, status, actor.id)
        return Result.success(Unit)
    }

    private suspend fun enqueueJoinRequest(participant: TourParticipant, guideId: String) {
        val payload = JSONObject().apply {
            put("tourId", participant.tourId)
            put("userId", participant.userId)
            put("userName", participant.userName)
            put("status", participant.status.name)
            put("requestedAt", participant.requestedAt)
            put("guideId", guideId)
        }
        syncRepository.enqueue(
            type = SYNC_TYPE_JOIN_REQUEST,
            payloadId = "${participant.tourId}:${participant.userId}",
            payloadJson = payload.toString()
        )
    }

    private suspend fun enqueueParticipantUpdate(
        tourId: String,
        userId: String,
        status: TourParticipantStatus,
        actorId: String
    ) {
        val payload = JSONObject().apply {
            put("tourId", tourId)
            put("userId", userId)
            put("status", status.name)
            put("actorId", actorId)
            put("updatedAt", System.currentTimeMillis())
        }
        syncRepository.enqueue(
            type = SYNC_TYPE_PARTICIPANT_UPDATE,
            payloadId = "${tourId}:${userId}:${status.name}",
            payloadJson = payload.toString()
        )
    }

    companion object {
        const val SYNC_TYPE_JOIN_REQUEST = "tour_join_request"
        const val SYNC_TYPE_PARTICIPANT_UPDATE = "tour_participant_update"
    }
}
