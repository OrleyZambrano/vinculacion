package com.example.vinculacion.ui.map

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vinculacion.MainActivity
import com.example.vinculacion.R
import com.example.vinculacion.data.model.GuideRoute
import com.example.vinculacion.data.repository.AuthRepository
import com.example.vinculacion.databinding.FragmentTourRouteMapBinding
import com.example.vinculacion.ui.routes.RouteCreateFlowDialogFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import android.graphics.Color
import com.google.maps.android.ui.IconGenerator
import kotlinx.coroutines.launch

class TourRouteMapFragment : Fragment() {

    private var _binding: FragmentTourRouteMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapsViewModel by activityViewModels()
    private val authRepository by lazy { AuthRepository(requireContext()) }

    private var googleMap: GoogleMap? = null
    private var latestRoutes: List<GuideRoute> = emptyList()
    private var latestSelectedGeoJson: String? = null
    private var currentUserId: String? = null
    private var isGuide: Boolean = false
    private val routeMarkers = mutableListOf<Marker>()
    private var routeFilterAdapter: ArrayAdapter<String>? = null
    private var latestRouteFilter: String = ""

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
        setupActions()
        setupFilter()
        collectRoutes()
        collectSelectedRoute()
        collectAuthState()
    }

    private fun setupActions() {
        binding.routeCreateFab.setOnClickListener {
            if (!isGuide) return@setOnClickListener
            RouteCreateFlowDialogFragment.newInstance()
                .show(childFragmentManager, "routeCreateFlow")
        }
        binding.routeListButton.setOnClickListener {
            if (!isGuide) return@setOnClickListener
            (activity as? MainActivity)?.openMyRoutes()
        }
    }

    private fun setupFilter() {
        val input = binding.inputRouteFilter as AutoCompleteTextView
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                latestRouteFilter = s?.toString().orEmpty()
                renderRoutes(filterRoutes(buildRoutesToRender(latestRoutes, latestSelectedGeoJson)))
            }
        })
        input.setOnItemClickListener { parent, _, position, _ ->
            latestRouteFilter = parent.getItemAtPosition(position) as String
            renderRoutes(filterRoutes(buildRoutesToRender(latestRoutes, latestSelectedGeoJson)))
        }
    }

    private fun collectAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authRepository.authState.collect { state ->
                    currentUserId = state.profile.id
                    isGuide = state.profile.role.canManageTours()
                    binding.routeListButton.isVisible = isGuide
                    binding.routeCreateFab.isVisible = isGuide
                    binding.routeFilterCard.isVisible = !isGuide
                }
            }
        }
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
                viewModel.routes.collect { routes ->
                    latestRoutes = routes
                    updateRouteFilter(routes)
                    val toRender = filterRoutes(buildRoutesToRender(routes, latestSelectedGeoJson))
                    binding.routeEmptyMessage.isVisible = toRender.isEmpty()
                    renderRoutes(toRender)
                }
            }
        }
    }

    private fun collectSelectedRoute() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedRouteGeoJson.collect { geoJson ->
                    latestSelectedGeoJson = geoJson
                    val toRender = filterRoutes(buildRoutesToRender(latestRoutes, geoJson))
                    binding.routeEmptyMessage.isVisible = toRender.isEmpty()
                    renderRoutes(toRender)
                }
            }
        }
    }

    private fun updateRouteFilter(routes: List<GuideRoute>) {
        val labels = buildList {
            add(getString(R.string.route_filter_all))
            addAll(routes.map { it.title })
        }
        if (routeFilterAdapter == null || routeFilterAdapter?.count != labels.size) {
            routeFilterAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
            (binding.inputRouteFilter as AutoCompleteTextView).setAdapter(routeFilterAdapter)
        } else {
            routeFilterAdapter?.clear()
            routeFilterAdapter?.addAll(labels)
            routeFilterAdapter?.notifyDataSetChanged()
        }
        if (latestRouteFilter.isBlank()) {
            latestRouteFilter = getString(R.string.route_filter_all)
            (binding.inputRouteFilter as AutoCompleteTextView)
                .setText(latestRouteFilter, false)
        }
    }

    private fun filterRoutes(routes: List<GuideRoute>): List<GuideRoute> {
        val text = latestRouteFilter.trim()
        if (text.isBlank() || text == getString(R.string.route_filter_all)) return routes
        return routes.filter { it.title.contains(text, ignoreCase = true) }
    }

    private fun buildRoutesToRender(routes: List<GuideRoute>, selectedGeoJson: String?): List<GuideRoute> {
        return if (!selectedGeoJson.isNullOrBlank()) {
            listOf(
                GuideRoute(
                    id = "selected_route",
                    title = getString(R.string.tour_route_selected_title),
                    geoJson = selectedGeoJson,
                    guideId = currentUserId.orEmpty(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            routes
        }
    }

    private fun renderRoutes(routes: List<GuideRoute>) {
        val map = googleMap ?: return
        map.clear()
        routeMarkers.clear()
        if (routes.isEmpty()) return

        val bounds = LatLngBounds.Builder()
        var hasBounds = false

        routes.forEachIndexed { index, route ->
            val path = RouteParser.parseLineString(route.geoJson)
            if (path.size < 2) return@forEachIndexed

            val polylineOptions = PolylineOptions()
                .addAll(path)
                .color(colorForIndex(index, routes.size))
                .width(POLYLINE_WIDTH)
                .geodesic(true)
            map.addPolyline(polylineOptions)

            val start = path.firstOrNull()
            val end = path.lastOrNull()
            if (start != null && end != null) {
                addRouteLabelMarker(start, getString(R.string.tour_route_start))
                addRouteLabelMarker(end, getString(R.string.tour_route_end))
            }

            path.forEach { latLng ->
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


    private fun addRouteLabelMarker(position: LatLng, label: String) {
        val map = googleMap ?: return
        val generator = IconGenerator(requireContext())
        generator.setTextAppearance(R.style.TextAppearance_Vinculacion_MapLabel)
        generator.setColor(ContextCompat.getColor(requireContext(), R.color.surface_container))
        val paddingH = resources.getDimensionPixelSize(R.dimen.map_label_padding_horizontal)
        val paddingV = resources.getDimensionPixelSize(R.dimen.map_label_padding_vertical)
        generator.setContentPadding(paddingH, paddingV, paddingH, paddingV)
        val icon = generator.makeIcon(label)
        val marker = map.addMarker(
            MarkerOptions()
                .position(position)
                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(icon))
                .anchor(0.5f, 1.0f)
        )
        if (marker != null) routeMarkers.add(marker)
    }

    private fun colorForIndex(index: Int, total: Int): Int {
        if (total <= 0) return ContextCompat.getColor(requireContext(), R.color.primary_color)
        val hueStep = 360f / total
        val hue = (index * hueStep) % 360f
        return Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.9f))
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
    }
}
