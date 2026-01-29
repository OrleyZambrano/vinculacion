package com.example.vinculacion.ui.guide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.vinculacion.R
import com.example.vinculacion.data.model.GuideRoute
import com.example.vinculacion.databinding.DialogTourRouteEditorBinding
import com.example.vinculacion.ui.map.MapsViewModel
import com.example.vinculacion.ui.map.RouteParser
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar

// Vista dedicada para editar una ruta existente (nombre y puntos).
class TourRouteEditorDialogFragment : DialogFragment() {

    private var _binding: DialogTourRouteEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapsViewModel by activityViewModels()

    private var googleMap: GoogleMap? = null
    private val points = mutableListOf<LatLng>()
    private val markers = mutableListOf<Marker>()
    private var polyline: Polyline? = null

    private val routeId: String by lazy { requireArguments().getString(ARG_ROUTE_ID).orEmpty() }
    private val routeTitle: String by lazy { requireArguments().getString(ARG_ROUTE_TITLE).orEmpty() }
    private val routeGeoJson: String by lazy { requireArguments().getString(ARG_ROUTE_GEO_JSON).orEmpty() }
    private val routeGuideId: String by lazy { requireArguments().getString(ARG_ROUTE_GUIDE_ID).orEmpty() }
    private val routeCreatedAt: Long by lazy { requireArguments().getLong(ARG_ROUTE_CREATED_AT) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTourRouteEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.routeEditorTitle.text = getString(R.string.route_edit_title)
        binding.inputRouteName.setText(routeTitle)
        setupMap()
        setupActions()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.routeEditorMapContainer) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also { fragment ->
                childFragmentManager.beginTransaction()
                    .replace(R.id.routeEditorMapContainer, fragment)
                    .commitNow()
            }
        mapFragment.getMapAsync { map ->
            googleMap = map.apply {
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isMapToolbarEnabled = false
            }
            map.setOnMapClickListener { latLng -> addPoint(latLng) }
            map.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker) = Unit

                override fun onMarkerDragStart(marker: Marker) = Unit

                override fun onMarkerDragEnd(marker: Marker) {
                    val index = marker.tag as? Int ?: return
                    if (index in points.indices) {
                        points[index] = marker.position
                        redrawPolyline()
                    }
                }
            })
            seedRoutePoints()
            redrawPolyline()
        }
    }

    private fun setupActions() {
        binding.routeEditorUndo.setOnClickListener {
            removeLastPoint()
        }
        binding.routeEditorClear.setOnClickListener {
            clearAll()
        }
        binding.routeEditorSave.setOnClickListener {
            saveRoute()
        }
    }

    private fun addPoint(point: LatLng, map: GoogleMap? = googleMap, moveCamera: Boolean = true) {
        val resolvedMap = map ?: return
        points.add(point)
        val marker = resolvedMap.addMarker(
            MarkerOptions()
                .position(point)
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
        if (marker != null) {
            marker.tag = points.lastIndex
            markers.add(marker)
        }
        redrawPolyline()
        if (moveCamera) {
            resolvedMap.animateCamera(CameraUpdateFactory.newLatLng(point))
        }
    }

    private fun removeLastPoint() {
        if (points.isEmpty()) return
        points.removeLast()
        markers.lastOrNull()?.remove()
        markers.removeLastOrNull()
        syncMarkerTags()
        redrawPolyline()
    }

    private fun clearAll() {
        points.clear()
        markers.forEach { it.remove() }
        markers.clear()
        polyline?.remove()
        polyline = null
    }

    private fun redrawPolyline() {
        val map = googleMap ?: return
        polyline?.remove()
        if (points.size < 2) return
        polyline = map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(requireContext().getColor(R.color.primary_color))
                .width(POLYLINE_WIDTH)
        )
        fitBounds()
    }

    private fun fitBounds() {
        val map = googleMap ?: return
        if (points.isEmpty()) return
        val bounds = LatLngBounds.Builder()
        points.forEach { bounds.include(it) }
        map.setOnMapLoadedCallback {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), CAMERA_PADDING))
        }
    }

    private fun syncMarkerTags() {
        markers.forEachIndexed { index, marker ->
            marker.tag = index
        }
    }

    private fun saveRoute() {
        if (points.size < 2) {
            Snackbar.make(binding.root, getString(R.string.tour_route_min_points), Snackbar.LENGTH_SHORT).show()
            return
        }
        val title = binding.inputRouteName.text?.toString()?.trim()
        if (title.isNullOrBlank()) {
            Snackbar.make(binding.root, getString(R.string.tour_route_name_hint), Snackbar.LENGTH_SHORT).show()
            return
        }
        val geoJson = RouteParser.buildLineString(points)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.updateRoute(
                GuideRoute(
                    id = routeId,
                    title = title,
                    geoJson = geoJson,
                    guideId = routeGuideId,
                    createdAt = routeCreatedAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (result.isSuccess) {
                Snackbar.make(binding.root, getString(R.string.route_edit_success), Snackbar.LENGTH_SHORT).show()
                dismiss()
            } else {
                Snackbar.make(binding.root, getString(R.string.route_edit_error), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun seedRoutePoints() {
        if (routeGeoJson.isBlank()) return
        val initial = RouteParser.parseLineString(routeGeoJson)
        if (initial.isEmpty()) return
        points.clear()
        points.addAll(initial)
        markers.forEach { it.remove() }
        markers.clear()
        initial.forEachIndexed { index, point ->
            val marker = googleMap?.addMarker(
                MarkerOptions()
                    .position(point)
                    .draggable(true)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            if (marker != null) {
                marker.tag = index
                markers.add(marker)
            }
        }
        fitBounds()
    }

    override fun onDestroyView() {
        googleMap = null
        polyline = null
        markers.clear()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val POLYLINE_WIDTH = 8f
        private const val CAMERA_PADDING = 80

        private const val ARG_ROUTE_ID = "route_id"
        private const val ARG_ROUTE_TITLE = "route_title"
        private const val ARG_ROUTE_GEO_JSON = "route_geo_json"
        private const val ARG_ROUTE_GUIDE_ID = "route_guide_id"
        private const val ARG_ROUTE_CREATED_AT = "route_created_at"

        fun newInstance(route: GuideRoute): TourRouteEditorDialogFragment {
            return TourRouteEditorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ROUTE_ID, route.id)
                    putString(ARG_ROUTE_TITLE, route.title)
                    putString(ARG_ROUTE_GEO_JSON, route.geoJson)
                    putString(ARG_ROUTE_GUIDE_ID, route.guideId)
                    putLong(ARG_ROUTE_CREATED_AT, route.createdAt)
                }
            }
        }
    }
}
