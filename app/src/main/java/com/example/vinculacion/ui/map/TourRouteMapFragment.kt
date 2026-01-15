package com.example.vinculacion.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vinculacion.R
import com.example.vinculacion.databinding.FragmentTourRouteMapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch

class TourRouteMapFragment : Fragment() {

    private var _binding: FragmentTourRouteMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapsViewModel by activityViewModels()

    private var googleMap: GoogleMap? = null
    private var latestRoutes: List<TourRoute> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTourRouteMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        collectRoutes()
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.routeMapContainer) as? SupportMapFragment
            ?: return
        mapFragment.getMapAsync { map ->
            googleMap = map.apply {
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isMapToolbarEnabled = false
            }
            renderRoutes(latestRoutes)
        }
    }

    private fun collectRoutes() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tourRoutes.collect { routes ->
                    latestRoutes = routes
                    binding.routeEmptyMessage.isVisible = routes.isEmpty()
                    renderRoutes(routes)
                }
            }
        }
    }

    private fun renderRoutes(routes: List<TourRoute>) {
        val map = googleMap ?: return
        map.clear()
        if (routes.isEmpty()) return

        val colors = routeColors()
        val bounds = LatLngBounds.Builder()
        var hasBounds = false

        routes.forEachIndexed { index, route ->
            if (route.path.size < 2) return@forEachIndexed

            val polylineOptions = PolylineOptions()
                .addAll(route.path)
                .color(colors[index % colors.size])
                .width(POLYLINE_WIDTH)
            map.addPolyline(polylineOptions)

            route.meetingPoint?.let { point ->
                map.addMarker(
                    MarkerOptions()
                        .position(point)
                        .title(route.tour.title)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
                includePoint(bounds, point)
                hasBounds = true
            }

            route.waypoints.forEach { waypoint ->
                map.addMarker(
                    MarkerOptions()
                        .position(waypoint)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
                        .alpha(WAYPOINT_ALPHA)
                )
                includePoint(bounds, waypoint)
                hasBounds = true
            }

            route.path.forEach { latLng ->
                includePoint(bounds, latLng)
                hasBounds = true
            }
        }

        if (hasBounds) {
            map.setOnMapLoadedCallback {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), CAMERA_PADDING))
            }
        }
    }

    private fun routeColors(): List<Int> {
        val context = requireContext()
        return listOf(
            ContextCompat.getColor(context, R.color.primary_color),
            ContextCompat.getColor(context, R.color.accent_color),
            ContextCompat.getColor(context, R.color.primary_color_dark)
        )
    }

    private fun includePoint(builder: LatLngBounds.Builder, point: LatLng) {
        builder.include(point)
    }

    override fun onDestroyView() {
        googleMap = null
        latestRoutes = emptyList()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val POLYLINE_WIDTH = 8f
        private const val CAMERA_PADDING = 80
        private const val WAYPOINT_ALPHA = 0.8f
    }
}
