package com.example.vinculacion.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.vinculacion.R
import com.example.vinculacion.data.model.Weather
import com.example.vinculacion.data.repository.AvesRepository
import com.example.vinculacion.data.repository.WeatherRepository
import com.example.vinculacion.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "Weather"
    }

    private val repository = AvesRepository(application)
    private val weatherRepository = WeatherRepository(application)

    private val _weatherState = MutableStateFlow<UiState<Weather>>(UiState.Loading)
    val weatherState: StateFlow<UiState<Weather>> = _weatherState

    private val _homeState = MutableStateFlow<UiState<HomeUiData>>(UiState.Loading)
    val homeState: StateFlow<UiState<HomeUiData>> = _homeState

    private var isLoadingWeather = false
    private var lastWeatherLoadTime = 0L
    private val weatherLoadDebounceMs = 2000L // 2 segundos entre cargas

    private val quickActions by lazy {
        listOf(
            HomeQuickAction(
                iconRes = R.drawable.ic_categories,
                title = application.getString(R.string.home_action_categories),
                subtitle = application.getString(R.string.home_action_categories_subtitle),
                action = HomeAction.CATEGORIES
            ),
            HomeQuickAction(
                iconRes = R.drawable.ic_tours,
                title = application.getString(R.string.home_action_tours),
                subtitle = application.getString(R.string.home_action_tours_subtitle),
                action = HomeAction.TOURS
            ),
            HomeQuickAction(
                iconRes = R.drawable.ic_option2,
                title = application.getString(R.string.home_action_map),
                subtitle = application.getString(R.string.home_action_map_subtitle),
                action = HomeAction.MAP
            ),
            HomeQuickAction(
                iconRes = R.drawable.ic_audio,
                title = application.getString(R.string.home_action_recognition),
                subtitle = application.getString(R.string.home_action_recognition_subtitle),
                action = HomeAction.RECOGNITION
            ),
            HomeQuickAction(
                iconRes = R.drawable.ic_profile,
                title = application.getString(R.string.home_action_profile),
                subtitle = application.getString(R.string.home_action_profile_subtitle),
                action = HomeAction.PROFILE
            )
        )
    }

    init {
        observeHomeStreams()
        refreshAves()
        loadWeather()
    }

    fun defaultQuickActions(): List<HomeQuickAction> = quickActions

    fun refresh() {
        refreshAves(forceRefresh = true)
        loadWeather()
    }

    fun loadWeather() {
        val now = System.currentTimeMillis()
        
        // Evitar múltiples llamadas en poco tiempo
        if (isLoadingWeather) {
            Log.d(TAG, "Weather load already in progress, skipping")
            return
        }
        
        if ((now - lastWeatherLoadTime) < weatherLoadDebounceMs) {
            Log.d(TAG, "Weather loaded recently, skipping (${now - lastWeatherLoadTime}ms ago)")
            return
        }

        viewModelScope.launch {
            isLoadingWeather = true
            lastWeatherLoadTime = now
            Log.d(TAG, "loadWeather started")
            _weatherState.value = UiState.Loading
            
            val locationResult = weatherRepository.getCurrentLocation()
            if (locationResult.isFailure) {
                Log.e(TAG, "loadWeather location failed", locationResult.exceptionOrNull())
                _weatherState.value = UiState.Error(
                    locationResult.exceptionOrNull() ?: Exception("Error obteniendo ubicación")
                )
                isLoadingWeather = false
                return@launch
            }

            val location = locationResult.getOrNull()
            if (location == null) {
                isLoadingWeather = false
                return@launch
            }
            
            val weatherResult = weatherRepository.getCurrentWeather(location)
            
            _weatherState.value = if (weatherResult.isSuccess) {
                Log.d(TAG, "loadWeather success")
                UiState.Success(weatherResult.getOrNull()!!)
            } else {
                Log.e(TAG, "loadWeather weather failed", weatherResult.exceptionOrNull())
                UiState.Error(weatherResult.exceptionOrNull() ?: Exception("Error obteniendo clima"))
            }
            
            isLoadingWeather = false
        }
    }

    private fun observeHomeStreams() {
        viewModelScope.launch {
            combine(
                repository.observeTopAves(limit = 5),
                repository.observeCategorias()
            ) { topBirds, categories ->
                HomeUiData(topBirds, categories, quickActions)
            }
                .catch { throwable ->
                    _homeState.value = UiState.Error(throwable)
                }
                .collect { data ->
                    _homeState.value = if (data.topBirds.isEmpty() && data.categories.isEmpty()) {
                        UiState.Empty()
                    } else {
                        UiState.Success(data)
                    }
                }
        }
    }

    private fun refreshAves(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _homeState.value = UiState.Loading
            val result = repository.getAves(forceRefresh)
            result.exceptionOrNull()?.let { throwable ->
                _homeState.value = UiState.Error(throwable)
            }
        }
    }
}
