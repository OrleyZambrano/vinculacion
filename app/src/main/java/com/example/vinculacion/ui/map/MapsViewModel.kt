package com.example.vinculacion.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinculacion.data.local.room.entities.TourStatus
import com.example.vinculacion.data.model.MediaRecord
import com.example.vinculacion.data.model.Tour
import com.example.vinculacion.data.repository.MediaRepository
import com.example.vinculacion.data.repository.ToursRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MapsViewModel(application: Application) : AndroidViewModel(application) {

    private val toursRepository = ToursRepository(application)
    private val mediaRepository = MediaRepository(application)

    private val _tourRoutes = MutableStateFlow<List<TourRoute>>(emptyList())
    val tourRoutes: StateFlow<List<TourRoute>> = _tourRoutes

    private val _heatmapData = MutableStateFlow(HeatmapData(emptyList(), emptyList()))
    val heatmapData: StateFlow<HeatmapData> = _heatmapData

    init {
        observeTours()
        observeHeatmap()
    }

    private fun observeTours() {
        viewModelScope.launch {
            toursRepository.observeTours().collectLatest { tours ->
                val filtered = tours.filter { it.status in activeStatuses }
                _tourRoutes.value = filtered.map { tour ->
                    val path = RouteParser.parseLineString(tour.routeGeoJson)
                    val waypoints = path.drop(1).dropLast(1)
                    val meetingPoint = tour.meetingPointLat?.let { lat ->
                        tour.meetingPointLng?.let { lng -> LatLng(lat, lng) }
                    }
                    TourRoute(
                        tour = tour,
                        path = path,
                        waypoints = waypoints,
                        meetingPoint = meetingPoint
                    )
                }
            }
        }
    }

    private fun observeHeatmap() {
        viewModelScope.launch {
            mediaRepository.observeWithLocation().collectLatest { records ->
                val points = records.mapNotNull { it.toLatLng() }
                _heatmapData.value = HeatmapData(records, points)
            }
        }
    }

    private fun MediaRecord.toLatLng(): LatLng? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        return LatLng(lat, lng)
    }

    companion object {
        private val activeStatuses = setOf(
            TourStatus.PUBLISHED,
            TourStatus.IN_PROGRESS
        )
    }
}
