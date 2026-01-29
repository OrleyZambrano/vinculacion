package com.example.vinculacion.ui.routes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinculacion.data.model.GuideRoute
import com.example.vinculacion.data.repository.AuthRepository
import com.example.vinculacion.data.repository.RoutesRepository
import com.example.vinculacion.ui.common.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MyRoutesViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application)
    private val routesRepository = RoutesRepository(application)

    private val _uiState = MutableStateFlow<UiState<List<GuideRoute>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<GuideRoute>>> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MyRoutesEvent>()
    val events = _events.asSharedFlow()

    init {
        observeRoutes()
        viewModelScope.launch {
            routesRepository.syncFromRemote()
        }
    }

    private fun observeRoutes() {
        viewModelScope.launch {
            authRepository.authState
                .flatMapLatest { auth ->
                    val guideId = auth.profile.id
                    routesRepository.observeRoutesByGuide(guideId)
                }
                .collectLatest { routes ->
                    _uiState.value = if (routes.isEmpty()) {
                        UiState.Empty()
                    } else {
                        UiState.Success(routes)
                    }
                }
        }
    }

    fun deleteRoute(route: GuideRoute) {
        viewModelScope.launch {
            val result = routesRepository.deleteRoute(route.id)
            if (result.isSuccess) {
                _events.emit(MyRoutesEvent.ShowMessage(MyRoutesMessage.DeleteSuccess))
            } else {
                _events.emit(MyRoutesEvent.ShowMessage(MyRoutesMessage.DeleteError))
            }
        }
    }
}

sealed class MyRoutesEvent {
    data class ShowMessage(val message: MyRoutesMessage) : MyRoutesEvent()
}

enum class MyRoutesMessage {
    DeleteSuccess,
    DeleteError
}
