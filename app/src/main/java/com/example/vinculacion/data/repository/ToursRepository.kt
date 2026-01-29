package com.example.vinculacion.data.repository

import android.content.Context
import android.util.Log
import com.example.vinculacion.BuildConfig
import com.example.vinculacion.data.local.room.VinculacionDatabase
import com.example.vinculacion.data.local.room.entities.TourParticipantStatus
import com.example.vinculacion.data.local.room.mappers.toDomain
import com.example.vinculacion.data.local.room.mappers.toEntity
import com.example.vinculacion.data.model.Tour
import com.example.vinculacion.data.model.TourParticipant
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ToursRepository(context: Context) {

    private val database = VinculacionDatabase.getInstance(context)
    private val toursDao = database.toursDao()
    private val participantsDao = database.tourParticipantsDao()
    
    // Firebase Firestore
    private val firestore = FirebaseFirestore.getInstance()
    private val toursCollection = firestore.collection("tours")
    private val participantsCollection = firestore.collection("tour_participants")

    fun observeTours(): Flow<List<Tour>> =
        toursDao.observeTours().map { entities -> entities.map { it.toDomain() } }

    suspend fun getTour(id: String): Tour? = withContext(Dispatchers.IO) {
        toursDao.getTour(id)?.toDomain()
    }

    suspend fun upsertTour(tour: Tour) = withContext(Dispatchers.IO) {
        toursDao.upsert(tour.toEntity())
    }

    suspend fun upsertTours(tours: List<Tour>) = withContext(Dispatchers.IO) {
        toursDao.upsert(tours.map { it.toEntity() })
    }

    suspend fun syncFromRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val toursSnapshot = toursCollection
                .get()
                .await()

            val tours = toursSnapshot.documents.mapNotNull { it.toTourDomain() }
            toursDao.replaceAll(tours.map { it.toEntity() })

            val participantsSnapshot = participantsCollection.get().await()
            val participants = participantsSnapshot.documents.mapNotNull { it.toParticipantDomain() }
            participantsDao.replaceAll(participants.map { it.toEntity() })

            Result.success(Unit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("ToursRepository", "Error syncing from Firestore", e)
            }
            Result.failure(e)
        }
    }
    
    suspend fun createTour(tour: Tour): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Save to Firebase first
            val tourData = mapOf(
                "id" to tour.id,
                "title" to tour.title,
                "description" to tour.description,
                "guideId" to tour.guideId,
                "guideName" to tour.guideName,
                "guidePhone" to tour.guidePhone,
                "guideEmail" to tour.guideEmail,
                "status" to tour.status.name,
                "startTimeEpoch" to tour.startTimeEpoch,
                "endTimeEpoch" to tour.endTimeEpoch,
                "meetingPointLat" to tour.meetingPointLat,
                "meetingPointLng" to tour.meetingPointLng,
                "capacity" to tour.capacity,
                "suggestedPrice" to tour.suggestedPrice,
                "routeId" to tour.routeId,
                    "meetingPoint" to tour.meetingPoint,
                    "coverImageUrl" to tour.coverImageUrl,
                    "difficulty" to tour.difficulty,
                    "routeGeoJson" to tour.routeGeoJson,
                    "notes" to tour.notes,
                    "isLocalOnly" to tour.isLocalOnly,
                    "createdAt" to tour.createdAt,
                    "updatedAt" to tour.updatedAt
            )
            
            toursCollection.document(tour.id).set(tourData).await()
            
            // Also save locally for offline access
            toursDao.upsert(tour.toEntity())
            
            if (BuildConfig.DEBUG) {
                Log.d("ToursRepository", "Tour saved to Firebase: ${tour.id}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTourRoute(tourId: String, routeGeoJson: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updatedAt = System.currentTimeMillis()
            val updates = mapOf(
                "routeGeoJson" to routeGeoJson,
                "updatedAt" to updatedAt
            )

            toursCollection.document(tourId).update(updates).await()

            val current = toursDao.getTour(tourId)?.toDomain()
            if (current != null) {
                val updatedTour = current.copy(
                    routeGeoJson = routeGeoJson,
                    updatedAt = updatedAt
                )
                toursDao.upsert(updatedTour.toEntity())
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGuideName(guideId: String, guideName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val snapshot = toursCollection
                .whereEqualTo("guideId", guideId)
                .get()
                .await()

            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.update(doc.reference, "guideName", guideName)
            }
            if (!snapshot.isEmpty) {
                batch.commit().await()
            }

            toursDao.updateGuideName(guideId, guideName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTour(id: String) = withContext(Dispatchers.IO) {
        toursDao.delete(id)
    }

    fun observeParticipants(tourId: String): Flow<List<TourParticipant>> =
        participantsDao.observeByTour(tourId).map { list -> list.map { it.toDomain() } }

    fun observeAllParticipants(): Flow<List<TourParticipant>> =
        participantsDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeParticipantsByUser(userId: String): Flow<List<TourParticipant>> =
        participantsDao.observeByUser(userId).map { list -> list.map { it.toDomain() } }

    suspend fun getParticipant(tourId: String, userId: String): TourParticipant? = withContext(Dispatchers.IO) {
        participantsDao.getParticipant(tourId, userId)?.toDomain()
    }

    suspend fun requestJoin(participant: TourParticipant): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Save to Firebase first
            val participantData = mapOf(
                "id" to participant.id,
                "tourId" to participant.tourId,
                "userId" to participant.userId,
                "userName" to participant.userName,
                "userPhone" to participant.userPhone,
                "userEmail" to participant.userEmail,
                "status" to participant.status.name,
                "requestedAt" to participant.requestedAt,
                "processedAt" to participant.processedAt,
                "notes" to participant.notes
            )
            
            participantsCollection.document(participant.id).set(participantData).await()
            
            // Also save locally
            participantsDao.upsert(participant.toEntity())
            
            if (BuildConfig.DEBUG) {
                Log.d("ToursRepository", "Participant request saved: ${participant.id}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateParticipantStatus(tourId: String, userId: String, status: TourParticipantStatus): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                var participant = getParticipant(tourId, userId)
                if (participant == null) {
                    // Try syncing from remote before failing
                    syncParticipantsForTour(tourId)
                    participant = getParticipant(tourId, userId)
                }

                if (participant != null) {
                    val processedAt = System.currentTimeMillis()
                    val updates = mapOf(
                        "status" to status.name,
                        "processedAt" to processedAt
                    )
                    
                    participantsCollection.document(participant.id).update(updates).await()
                    
                    // Update locally
                    participantsDao.updateStatus(
                        tourId = tourId,
                        userId = userId,
                        status = status,
                        processedAt = processedAt
                    )
                    
                    if (BuildConfig.DEBUG) {
                        Log.d("ToursRepository", "Participant status updated: $status")
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Participant not found"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun getParticipantsByTour(tourId: String): List<TourParticipant> = withContext(Dispatchers.IO) {
        syncParticipantsForTour(tourId)
        participantsDao.getByTour(tourId).map { it.toDomain() }
    }

    suspend fun removeParticipant(participant: TourParticipant) = withContext(Dispatchers.IO) {
        participantsDao.delete(participant.toEntity())
    }

    private suspend fun syncParticipantsForTour(tourId: String) {
        try {
            val snapshot = participantsCollection
                .whereEqualTo("tourId", tourId)
                .get()
                .await()

            val participants = snapshot.documents.mapNotNull { it.toParticipantDomain() }
            participantsDao.replaceForTour(tourId, participants.map { it.toEntity() })
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("ToursRepository", "Error syncing participants for tour $tourId", e)
            }
        }
    }

    private fun DocumentSnapshot.toTourDomain(): Tour? {
        val tourId = getString("id") ?: getString("tourId") ?: id
        val title = getString("title") ?: return null
        val guideId = getString("guideId") ?: return null
        val statusString = getString("status") ?: com.example.vinculacion.data.local.room.entities.TourStatus.PUBLISHED.name
        val status = runCatching { com.example.vinculacion.data.local.room.entities.TourStatus.valueOf(statusString) }
            .getOrDefault(com.example.vinculacion.data.local.room.entities.TourStatus.PUBLISHED)

        val startEpoch = readEpoch("startTimeEpoch")
            ?: readEpoch("startDate")
            ?: System.currentTimeMillis()
        val endEpoch = readEpoch("endTimeEpoch") ?: readEpoch("endDate")

        val createdAt = readEpoch("createdAt") ?: System.currentTimeMillis()
        val updatedAt = readEpoch("updatedAt") ?: createdAt

        return Tour(
            id = tourId,
            title = title,
            description = getString("description"),
            guideId = guideId,
            guideName = getString("guideName"),
            guidePhone = getString("guidePhone") ?: getString("whatsapp"),
            guideEmail = getString("guideEmail"),
            coverImageUrl = getString("coverImageUrl"),
            difficulty = getString("difficulty"),
            status = status,
            startTimeEpoch = startEpoch,
            endTimeEpoch = endEpoch,
            meetingPoint = getString("meetingPoint"),
            meetingPointLat = readDouble("meetingPointLat"),
            meetingPointLng = readDouble("meetingPointLng"),
            capacity = readInt("capacity"),
            suggestedPrice = readDouble("suggestedPrice") ?: readDouble("price"),
            routeId = getString("routeId"),
            routeGeoJson = getString("routeGeoJson"),
            notes = getString("notes"),
            createdAt = createdAt,
            updatedAt = updatedAt,
            isLocalOnly = getBoolean("isLocalOnly") ?: false
        )
    }

    private fun DocumentSnapshot.toParticipantDomain(): TourParticipant? {
        val tourId = getString("tourId") ?: return null
        val userId = getString("userId") ?: return null
        val participantId = getString("id") ?: id
        val statusString = getString("status") ?: TourParticipantStatus.PENDING.name
        val status = runCatching { TourParticipantStatus.valueOf(statusString) }
            .getOrDefault(TourParticipantStatus.PENDING)

        val requestedAt = readEpoch("requestedAt")
            ?: readEpoch("createdAt")
            ?: System.currentTimeMillis()
        val processedAt = readEpoch("processedAt") ?: readEpoch("updatedAt")

        return TourParticipant(
            id = participantId,
            tourId = tourId,
            userId = userId,
            userName = getString("userName") ?: "",
            userPhone = getString("userPhone") ?: "",
            userEmail = getString("userEmail") ?: "",
            status = status,
            requestedAt = requestedAt,
            processedAt = processedAt,
            notes = getString("notes") ?: ""
        )
    }

    private fun DocumentSnapshot.readEpoch(field: String): Long? {
        val fromLong = getLong(field)
        if (fromLong != null) return fromLong
        val fromTimestamp: Timestamp? = getTimestamp(field)
        return fromTimestamp?.toDate()?.time
    }

    private fun DocumentSnapshot.readDouble(field: String): Double? {
        return when (val value = get(field)) {
            is Double -> value
            is Long -> value.toDouble()
            else -> null
        }
    }

    private fun DocumentSnapshot.readInt(field: String): Int? {
        return getLong(field)?.toInt()
    }
}
