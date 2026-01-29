package com.example.vinculacion.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinculacion.data.local.room.entities.TourStatus
import com.example.vinculacion.data.model.Ave
import com.example.vinculacion.data.model.GuideRoute
import com.example.vinculacion.data.model.MediaRecord
import com.example.vinculacion.data.model.Tour
import com.example.vinculacion.data.repository.AuthRepository
import com.example.vinculacion.data.repository.AvesRepository
import com.example.vinculacion.data.repository.MediaRepository
import com.example.vinculacion.data.repository.RoutesRepository
import com.example.vinculacion.data.repository.ToursRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MapsViewModel(application: Application) : AndroidViewModel(application) {

    private val toursRepository = ToursRepository(application)
    private val mediaRepository = MediaRepository(application)
    private val avesRepository = AvesRepository(application)
    private val routesRepository = RoutesRepository(application)
    private val authRepository = AuthRepository(application)

    private val _tourRoutes = MutableStateFlow<List<TourRoute>>(emptyList())
    val tourRoutes: StateFlow<List<TourRoute>> = _tourRoutes

    private val _heatmapData = MutableStateFlow(HeatmapData(emptyList(), emptyList()))
    val heatmapData: StateFlow<HeatmapData> = _heatmapData

    private val _routes = MutableStateFlow<List<GuideRoute>>(emptyList())
    val routes: StateFlow<List<GuideRoute>> = _routes.asStateFlow()

    private val _selectedRouteGeoJson = MutableStateFlow<String?>(null)
    val selectedRouteGeoJson: StateFlow<String?> = _selectedRouteGeoJson.asStateFlow()

    private val _aves = MutableStateFlow<List<Ave>>(emptyList())
    val aves: StateFlow<List<Ave>> = _aves.asStateFlow()

    private val _selectedAveId = MutableStateFlow<Long?>(null)
    val selectedAveId: StateFlow<Long?> = _selectedAveId.asStateFlow()

    init {
        observeAves()
        observeTours()
        observeHeatmap()
        observeRoutes()
        viewModelScope.launch {
            mediaRepository.syncFromRemote()
            routesRepository.syncFromRemote()
        }
    }

    fun setSelectedRouteGeoJson(geoJson: String?) {
        _selectedRouteGeoJson.value = geoJson
    }


    suspend fun createRoute(title: String, geoJson: String): Result<Unit> {
        val auth = authRepository.authState.first()
        if (!auth.isAuthenticated || !auth.profile.role.canManageTours()) {
            return Result.failure(IllegalStateException("Solo los guías pueden crear rutas"))
        }
        val now = System.currentTimeMillis()
        val route = GuideRoute(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            geoJson = geoJson,
            guideId = auth.profile.id,
            createdAt = now,
            updatedAt = now
        )
        return routesRepository.createRoute(route)
    }

    suspend fun deleteRoute(routeId: String): Result<Unit> {
        val auth = authRepository.authState.first()
        if (!auth.isAuthenticated || !auth.profile.role.canManageTours()) {
            return Result.failure(IllegalStateException("Solo los guías pueden eliminar rutas"))
        }
        return routesRepository.deleteRoute(routeId)
    }

    suspend fun updateRoute(route: GuideRoute): Result<Unit> {
        val auth = authRepository.authState.first()
        if (!auth.isAuthenticated || !auth.profile.role.canManageTours()) {
            return Result.failure(IllegalStateException("Solo los guías pueden actualizar rutas"))
        }
        return routesRepository.updateRoute(route)
    }

    fun setSelectedAveId(aveId: Long?) {
        _selectedAveId.value = aveId
    }

    private fun observeAves() {
        viewModelScope.launch {
            avesRepository.observeAves().collectLatest { list ->
                _aves.value = list
            }
        }
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

    private fun observeRoutes() {
        viewModelScope.launch {
            combine(authRepository.authState, routesRepository.observeRoutes()) { auth, routes ->
                if (auth.profile.role.canManageTours()) {
                    routes.filter { it.guideId == auth.profile.id }
                } else {
                    routes
                }
            }.collectLatest { list ->
                _routes.value = list
            }
        }
    }

    private fun observeHeatmap() {
        viewModelScope.launch {
            combine(
                mediaRepository.observeWithLocation(),
                selectedAveId
            ) { records, aveId ->
                filterRecords(records, aveId)
            }.collectLatest { records ->
                val aveId = selectedAveId.value
                if (records.isEmpty()) {
                    mediaRepository.syncFromRemote()
                    val refreshed = mediaRepository.getWithLocation()
                    val refreshedFiltered = filterRecords(refreshed, aveId)
                    if (refreshedFiltered.isNotEmpty()) {
                        val refreshedPoints = refreshedFiltered.mapNotNull { it.toLatLngWithDemoOffset() }
                        _heatmapData.value = HeatmapData(refreshedFiltered, refreshedPoints)
                        return@collectLatest
                    }
                    val remote = mediaRepository.getRemoteWithLocation()
                    val remoteFiltered = filterRecords(remote, aveId)
                    val remotePoints = remoteFiltered.mapNotNull { it.toLatLngWithDemoOffset() }
                    _heatmapData.value = HeatmapData(remoteFiltered, remotePoints)
                    return@collectLatest
                }
                val points = records.mapNotNull { it.toLatLngWithDemoOffset() }
                _heatmapData.value = HeatmapData(records, points)
            }
        }
    }

    private fun filterRecords(records: List<MediaRecord>, aveId: Long?): List<MediaRecord> {
        return if (aveId == null) {
            records
        } else {
            records.filter { record -> effectiveAveId(record) == aveId }
        }
    }

    private fun MediaRecord.toLatLngWithDemoOffset(): LatLng? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        val demo = demoAves()
        if (aveId != null || demo.isEmpty()) {
            return LatLng(lat, lng)
        }
        val index = (id % demo.size).toInt()
        val offset = demoOffsets().getOrNull(index) ?: (0.0 to 0.0)
        return LatLng(lat + offset.first, lng + offset.second)
    }

    private fun demoOffsets(): List<Pair<Double, Double>> = listOf(
        0.03 to 0.03,
        -0.03 to 0.02,
        0.02 to -0.03
    )

    private fun effectiveAveId(record: MediaRecord): Long? {
        record.aveId?.let { return it }
        val demo = demoAves()
        if (demo.isEmpty()) return null
        val index = (record.id % demo.size).toInt()
        return demo[index].id.toLong()
    }

    private fun demoAves(): List<Ave> = _aves.value.take(3)

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
