package com.example.vinculacion.ui.tours

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinculacion.R
import com.example.vinculacion.core.network.NetworkMonitor
import com.example.vinculacion.data.local.room.entities.TourParticipantStatus
import com.example.vinculacion.data.model.AuthState
import com.example.vinculacion.data.model.Tour
import com.example.vinculacion.data.model.UserProfile
import com.example.vinculacion.data.model.UserRole
import com.example.vinculacion.data.repository.AuthRepository
import com.example.vinculacion.data.repository.ToursRepository
import com.example.vinculacion.domain.tours.TourParticipationManager
import com.example.vinculacion.ui.common.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ToursViewModel(application: Application) : AndroidViewModel(application) {

    private val toursRepository = ToursRepository(application)
    private val authRepository = AuthRepository(application)
    private val participationManager = TourParticipationManager(application)
    private val networkMonitor = NetworkMonitor(application)

    private val _uiState = MutableStateFlow<UiState<List<TourCardUi>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<TourCardUi>>> = _uiState

    private val _events = Channel<TourEvent>(Channel.BUFFERED)
    val events: Flow<TourEvent> = _events.receiveAsFlow()
    
    // Search and Filter state
    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilters = MutableStateFlow(setOf<String>("all"))
    private val _allTours = MutableStateFlow<List<TourCardUi>>(emptyList())

    val authState: StateFlow<AuthState> = authRepository.authState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState(UserProfile.guest(), false, null)
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val userParticipationFlow = authState.flatMapLatest { state ->
        if (!state.isAuthenticated) {
            flowOf(emptyList())
        } else {
            toursRepository.observeParticipantsByUser(state.profile.id)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val allParticipantsFlow = toursRepository.observeAllParticipants()

    init {
        observeTours()
    }

    fun applySearchAndFilters(query: String, filters: Set<String>) {
        _searchQuery.value = query
        _selectedFilters.value = filters
        filterAndSearchTours()
    }
    
    private fun filterAndSearchTours() {
        val allTours = _allTours.value
        val query = _searchQuery.value.lowercase()
        val filters = _selectedFilters.value
        val currentUser = authState.value.profile
        
        val filteredTours = allTours.filter { tourCard ->
            // Apply search filter
            val matchesSearch = if (query.isBlank()) {
                true
            } else {
                tourCard.tour.title.lowercase().contains(query) ||
                tourCard.tour.description?.lowercase()?.contains(query) == true ||
                tourCard.tour.guideName?.lowercase()?.contains(query) == true
            }
            
            // Apply category filters
            val matchesFilter = when {
                filters.contains("all") -> true
                filters.contains("my_tours") -> {
                    tourCard.tour.guideId == currentUser.id || 
                    tourCard.joinStatus != null
                }
                filters.contains("available") -> {
                    tourCard.tour.status == com.example.vinculacion.data.local.room.entities.TourStatus.PUBLISHED &&
                    tourCard.joinStatus == null
                }
                else -> true
            }
            
            matchesSearch && matchesFilter
        }
        
        _uiState.value = if (filteredTours.isEmpty() && allTours.isNotEmpty()) {
            UiState.Success(emptyList())
        } else {
            UiState.Success(filteredTours)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            toursRepository.syncFromRemote()
            val tours = toursRepository.observeTours().first()
            val items = buildUi(authState.value, tours, userParticipationFlow.value, allParticipantsFlow.first())
            _allTours.value = items
            filterAndSearchTours()
        }
    }

    fun currentAuthState(): AuthState = authState.value

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
            val auth = authState.value
            if (!auth.isAuthenticated || !auth.profile.role.canManageTours()) {
                _events.send(TourEvent.ShowError("Solo los guías pueden crear tours"))
                return@launch
            }
            
            try {
                val tour = Tour(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    description = description,
                    guideId = auth.profile.id,
                    guideName = auth.profile.displayName,
                    guidePhone = guidePhone ?: auth.profile.phone,
                    guideEmail = auth.profile.email,
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
                
                // Guardar en repositorio
                val result = toursRepository.createTour(tour)
                if (result.isSuccess) {
                    _events.send(TourEvent.ShowMessage(getApplication<Application>().getString(R.string.tour_create_success)))
                    refresh() // Actualizar lista
                } else {
                    _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_create_error)))
                }
            } catch (e: Exception) {
                _events.send(TourEvent.ShowError(e.localizedMessage ?: getApplication<Application>().getString(R.string.tour_create_error)))
            }
        }
    }

    fun requestJoin(tour: Tour) {
        viewModelScope.launch {
            val auth = authState.value
            if (!auth.isAuthenticated) {
                _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_status_guest_prompt)))
                return@launch
            }
            
            // Prevent guides from joining their own tours
            if (tour.guideId == auth.profile.id) {
                _events.send(TourEvent.ShowError("No puedes unirte a tu propio tour como guía"))
                return@launch
            }
            
            val existing = userParticipationFlow.value.firstOrNull { it.tourId == tour.id }
            val allowRequest = existing == null || existing.status == TourParticipantStatus.CANCELLED || existing.status == TourParticipantStatus.DECLINED
            if (!allowRequest) {
                _events.send(TourEvent.ShowMessage(getApplication<Application>().getString(R.string.tour_error_duplicate)))
                return@launch
            }
            val approvedCount = toursRepository.getParticipantsByTour(tour.id)
                .count { it.status == TourParticipantStatus.APPROVED }
            if (tour.capacity != null && approvedCount >= tour.capacity) {
                _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_capacity_full)))
                return@launch
            }
            val result = participationManager.requestJoin(tour, auth.profile)
            if (result.isSuccess) {
                _events.send(TourEvent.ShowMessage(getApplication<Application>().getString(R.string.tour_join_success, tour.title)))
            } else {
                _events.send(TourEvent.ShowError(result.exceptionOrNull()?.localizedMessage ?: getApplication<Application>().getString(R.string.tour_error_generic)))
            }
        }
    }

    fun cancelJoin(tour: Tour) {
        viewModelScope.launch {
            val auth = authState.value
            if (!auth.isAuthenticated) {
                _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_status_guest_prompt)))
                return@launch
            }
            val current = toursRepository.getParticipant(tour.id, auth.profile.id)
            if (current == null || (current.status != TourParticipantStatus.PENDING && current.status != TourParticipantStatus.APPROVED)) {
                _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_error_generic)))
                return@launch
            }
            val result = participationManager.cancelRequest(tour.id, auth.profile)
            if (result.isSuccess) {
                _events.send(TourEvent.ShowMessage(getApplication<Application>().getString(R.string.tour_cancel_success)))
            } else {
                _events.send(TourEvent.ShowError(result.exceptionOrNull()?.localizedMessage ?: getApplication<Application>().getString(R.string.tour_error_generic)))
            }
        }
    }

    fun approveParticipant(tourId: String, guideId: String, participantId: String) {
        viewModelScope.launch {
            val auth = authState.value
            if (!auth.profile.role.canManageTours() || auth.profile.id != guideId) {
                _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_error_permission)))
                return@launch
            }
            val tour = toursRepository.getTour(tourId)
            if (tour == null) {
                _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_error_generic)))
                return@launch
            }
            // Ensure latest participants from Firestore
            val participants = toursRepository.getParticipantsByTour(tourId)
            val approvedCount = participants.count { it.status == TourParticipantStatus.APPROVED }
            if (tour.capacity != null && approvedCount >= tour.capacity) {
                _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_capacity_full)))
                return@launch
            }
            val participant = toursRepository.getParticipant(tourId, participantId)
            if (participant == null) {
                _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_error_generic)))
                return@launch
            }
            val result = participationManager.updateParticipantStatus(tour, participant, TourParticipantStatus.APPROVED, auth.profile)
            if (result.isSuccess) {
                _events.send(TourEvent.ShowMessage(getApplication<Application>().getString(R.string.tour_approve_success)))
            } else {
                _events.send(TourEvent.ShowError(result.exceptionOrNull()?.localizedMessage ?: getApplication<Application>().getString(R.string.tour_error_generic)))
            }
        }
    }

    fun declineParticipant(tourId: String, guideId: String, participantId: String) {
        viewModelScope.launch {
            val auth = authState.value
            if (!auth.profile.role.canManageTours() || auth.profile.id != guideId) {
                _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_error_permission)))
                return@launch
            }
            val tour = toursRepository.getTour(tourId)
            if (tour == null) {
                _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_error_generic)))
                return@launch
            }
            toursRepository.getParticipantsByTour(tourId)
            val participant = toursRepository.getParticipant(tourId, participantId)
            if (participant == null) {
                _events.send(TourEvent.ShowError(getApplication<Application>().getString(R.string.tour_error_generic)))
                return@launch
            }
            val result = participationManager.updateParticipantStatus(tour, participant, TourParticipantStatus.DECLINED, auth.profile)
            if (result.isSuccess) {
                _events.send(TourEvent.ShowMessage(getApplication<Application>().getString(R.string.tour_decline_success)))
            } else {
                _events.send(TourEvent.ShowError(result.exceptionOrNull()?.localizedMessage ?: getApplication<Application>().getString(R.string.tour_error_generic)))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _events.send(TourEvent.ShowMessage(getApplication<Application>().getString(R.string.tour_sign_out_success)))
        }
    }

    fun observeParticipants(tourId: String): Flow<List<com.example.vinculacion.data.model.TourParticipant>> =
        toursRepository.observeParticipants(tourId)

    fun refreshParticipants(tourId: String) {
        viewModelScope.launch {
            toursRepository.getParticipantsByTour(tourId)
        }
    }

    private fun roleLabel(role: UserRole): String = when (role) {
        UserRole.INVITADO, UserRole.USUARIO -> getApplication<Application>().getString(R.string.tour_role_user)
        UserRole.GUIA -> getApplication<Application>().getString(R.string.tour_role_guide)
    }

    private fun observeTours() {
        viewModelScope.launch {
            toursRepository.syncFromRemote()
            combine(
                toursRepository.observeTours(),
                authState,
                userParticipationFlow,
                allParticipantsFlow
            ) { tours, auth, participations, allParticipants ->
                buildUi(auth, tours, participations, allParticipants)
            }.collect { items ->
                _allTours.value = items
                filterAndSearchTours()
            }
        }
    }

    private fun buildUi(
        auth: AuthState,
        tours: List<Tour>,
        participations: List<com.example.vinculacion.data.model.TourParticipant>,
        allParticipants: List<com.example.vinculacion.data.model.TourParticipant>
    ): List<TourCardUi> {
        return tours.map { tour ->
            val participation = participations.firstOrNull { it.tourId == tour.id }
            val approvedCount = allParticipants.count {
                it.tourId == tour.id && it.status == TourParticipantStatus.APPROVED
            }
            val remainingCapacity = tour.capacity?.let { capacity ->
                (capacity - approvedCount).coerceAtLeast(0)
            }
            val isOwnTour = auth.isAuthenticated && tour.guideId == auth.profile.id
            val canRequest = auth.isAuthenticated && 
                (participation == null || participation.status == TourParticipantStatus.CANCELLED || participation.status == TourParticipantStatus.DECLINED) && 
                tour.status.allowsRequests() &&
                !isOwnTour &&
                (remainingCapacity == null || remainingCapacity > 0) // Evitar solicitudes si no hay cupos
            val canCancel = auth.isAuthenticated && participation != null &&
                (participation.status == TourParticipantStatus.PENDING || participation.status == TourParticipantStatus.APPROVED)
            
            TourCardUi(
                tour = tour,
                joinStatus = participation?.status,
                requiresAuthentication = !auth.isAuthenticated,
                canRequestJoin = canRequest,
                canCancelJoin = canCancel,
                isGuide = isOwnTour,
                approvedCount = approvedCount,
                capacityRemaining = remainingCapacity
            )
        }
    }
}

sealed class TourEvent {
    data class ShowMessage(val message: String) : TourEvent()
    data class ShowError(val message: String) : TourEvent()
}
private fun com.example.vinculacion.data.local.room.entities.TourStatus.allowsRequests(): Boolean = when (this) {
    com.example.vinculacion.data.local.room.entities.TourStatus.PUBLISHED,
    com.example.vinculacion.data.local.room.entities.TourStatus.IN_PROGRESS -> true
    else -> false
}
