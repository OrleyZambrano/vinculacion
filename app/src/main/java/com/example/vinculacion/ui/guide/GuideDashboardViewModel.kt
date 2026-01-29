package com.example.vinculacion.ui.guide

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinculacion.R
import com.example.vinculacion.data.repository.AuthRepository
import com.example.vinculacion.data.repository.ToursRepository
import com.example.vinculacion.data.model.Tour
import com.example.vinculacion.ui.common.UiState
import com.example.vinculacion.ui.tours.TourCardUi
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class GuideDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val toursRepository = ToursRepository(application)
    private val authRepository = AuthRepository(application)

    private val _uiState = MutableStateFlow<UiState<List<TourCardUi>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<TourCardUi>>> = _uiState

    private val _events = Channel<GuideDashboardEvent>(Channel.BUFFERED)
    val events: Flow<GuideDashboardEvent> = _events.receiveAsFlow()

    fun loadMyTours() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            // Obtener el estado de auth actual (Flow -> first())
            val authState = authRepository.authState.first()
            if (!authState.isAuthenticated) {
                _events.send(GuideDashboardEvent.ShowError("No estás autenticado"))
                return@launch
            }

            val guideId = authState.profile.id
            
            try {
                toursRepository.syncFromRemote()
                combine(
                    toursRepository.observeTours(),
                    toursRepository.observeAllParticipants()
                ) { allTours, allParticipants ->
                    val myTours = allTours.filter { it.guideId == guideId }
                    myTours.map { tour ->
                        val approvedCount = allParticipants.count { participant ->
                            participant.tourId == tour.id &&
                                participant.status == com.example.vinculacion.data.local.room.entities.TourParticipantStatus.APPROVED
                        }
                        val remainingCapacity = tour.capacity?.let { capacity ->
                            (capacity - approvedCount).coerceAtLeast(0)
                        }
                        TourCardUi(
                            tour = tour,
                            joinStatus = null,
                            requiresAuthentication = false,
                            canRequestJoin = false,
                            canCancelJoin = false,
                            isGuide = true,
                            approvedCount = approvedCount,
                            capacityRemaining = remainingCapacity
                        )
                    }
                }.collect { tourCards ->
                    _uiState.value = if (tourCards.isEmpty()) {
                        UiState.Empty()
                    } else {
                        UiState.Success(tourCards)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(message = "Error al cargar tus tours: ${e.message}")
                _events.send(GuideDashboardEvent.ShowError(e.message.toString()))
            }
        }
    }

    fun refresh() {
        loadMyTours()
    }

    fun createTour(
        title: String,
        description: String?,
        dateTime: Long,
        meetingPoint: String,
        capacity: Int?,
        guidePhone: String?,
        routeId: String?,
        routeGeoJson: String?
    ) {
        viewModelScope.launch {
            val authState = authRepository.authState.first()
            if (!authState.isAuthenticated || !authState.profile.role.canManageTours()) {
                _events.send(GuideDashboardEvent.ShowError("Solo los guías pueden crear tours"))
                return@launch
            }

            try {
                val tour = Tour(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    description = description,
                    guideId = authState.profile.id,
                    guideName = authState.profile.displayName,
                    guidePhone = guidePhone ?: authState.profile.phone,
                    guideEmail = authState.profile.email,
                    coverImageUrl = null,
                    difficulty = null,
                    status = com.example.vinculacion.data.local.room.entities.TourStatus.PUBLISHED,
                    startTimeEpoch = dateTime,
                    endTimeEpoch = null,
                    meetingPoint = meetingPoint,
                    meetingPointLat = null,
                    meetingPointLng = null,
                    capacity = capacity,
                    suggestedPrice = null,
                    routeId = routeId,
                    routeGeoJson = routeGeoJson,
                    notes = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isLocalOnly = false
                )

                val result = toursRepository.createTour(tour)
                if (result.isSuccess) {
                    _events.send(GuideDashboardEvent.ShowSuccess(getApplication<Application>().getString(R.string.tour_create_success)))
                    loadMyTours()
                } else {
                    _events.send(GuideDashboardEvent.ShowError(getApplication<Application>().getString(R.string.tour_create_error)))
                }
            } catch (e: Exception) {
                _events.send(GuideDashboardEvent.ShowError(e.localizedMessage ?: getApplication<Application>().getString(R.string.tour_create_error)))
            }
        }
    }

    fun approveParticipant(tourId: String, userId: String) {
        viewModelScope.launch {
            val result = toursRepository.updateParticipantStatus(
                tourId, 
                userId, 
                com.example.vinculacion.data.local.room.entities.TourParticipantStatus.APPROVED
            )
            
            result.fold(
                onSuccess = {
                    _events.send(GuideDashboardEvent.ShowSuccess("Participante aprobado"))
                },
                onFailure = { error ->
                    _events.send(GuideDashboardEvent.ShowError("Error al aprobar: ${error.message}"))
                }
            )
        }
    }

    fun declineParticipant(tourId: String, userId: String) {
        viewModelScope.launch {
            val result = toursRepository.updateParticipantStatus(
                tourId, 
                userId, 
                com.example.vinculacion.data.local.room.entities.TourParticipantStatus.DECLINED
            )
            
            result.fold(
                onSuccess = {
                    _events.send(GuideDashboardEvent.ShowSuccess("Participante rechazado"))
                },
                onFailure = { error ->
                    _events.send(GuideDashboardEvent.ShowError("Error al rechazar: ${error.message}"))
                }
            )
        }
    }

    suspend fun getParticipants(tourId: String): List<com.example.vinculacion.data.model.TourParticipant> {
        return toursRepository.getParticipantsByTour(tourId)
    }

    fun updateTourRoute(tourId: String, routeGeoJson: String?) {
        viewModelScope.launch {
            val result = toursRepository.updateTourRoute(tourId, routeGeoJson)
            result.fold(
                onSuccess = {
                    _events.send(GuideDashboardEvent.ShowSuccess(getApplication<Application>().getString(R.string.tour_route_save_success)))
                    loadMyTours()
                },
                onFailure = { error ->
                    _events.send(GuideDashboardEvent.ShowError(error.localizedMessage ?: getApplication<Application>().getString(R.string.tour_route_save_error)))
                }
            )
        }
    }
}

sealed class GuideDashboardEvent {
    data class ShowError(val message: String) : GuideDashboardEvent()
    data class ShowSuccess(val message: String) : GuideDashboardEvent()
    data class NavigateToTourDetail(val tourId: String) : GuideDashboardEvent()
}