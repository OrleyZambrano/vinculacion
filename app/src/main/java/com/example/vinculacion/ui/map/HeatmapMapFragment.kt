package com.example.vinculacion.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.text.Editable
import android.text.TextWatcher
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vinculacion.R
import com.example.vinculacion.data.model.Ave
import com.example.vinculacion.data.model.MediaRecord
import com.example.vinculacion.databinding.FragmentHeatmapMapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.ui.IconGenerator
import java.util.Locale
import kotlinx.coroutines.launch

class HeatmapMapFragment : Fragment() {

    private var _binding: FragmentHeatmapMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapsViewModel by activityViewModels()

    private var googleMap: GoogleMap? = null
    private var heatmapProvider: HeatmapTileProvider? = null
    private var heatmapOverlay: TileOverlay? = null
    private var latestHeatmap: HeatmapData = HeatmapData(emptyList(), emptyList())
    private var latestAves: List<Ave> = emptyList()
    private val captureMarkers = mutableListOf<Marker>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHeatmapMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupFilter()
        collectHeatmap()
        collectAves()
    }

    private fun setupFilter() {
        val input = binding.heatmapFilterInput as AutoCompleteTextView
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty().trim()
                if (text.isBlank() || text == getString(R.string.heatmap_filter_all)) {
                    viewModel.setSelectedAveId(null)
                    return
                }
                val selected = latestAves.firstOrNull {
                    it.nombreComun.equals(text, ignoreCase = true) || it.titulo.equals(text, ignoreCase = true)
                }
                viewModel.setSelectedAveId(selected?.id?.toLong())
            }
        })
        input.setOnItemClickListener { parent, _, position, _ ->
            val label = parent.getItemAtPosition(position) as String
            if (label == getString(R.string.heatmap_filter_all)) {
                viewModel.setSelectedAveId(null)
            } else {
                val selected = latestAves.firstOrNull { it.nombreComun == label || it.titulo == label }
                viewModel.setSelectedAveId(selected?.id?.toLong())
            }
        }
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.heatmapContainer) as? SupportMapFragment
            ?: return
        mapFragment.getMapAsync { map ->
            googleMap = map.apply {
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isMapToolbarEnabled = false
                mapType = GoogleMap.MAP_TYPE_NORMAL
                setMinZoomPreference(MIN_ZOOM)
            }
            renderHeatmap(latestHeatmap)
            renderCaptureMarkers(latestHeatmap, latestAves)
        }
    }

    private fun collectHeatmap() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.heatmapData.collect { data ->
                    latestHeatmap = data
                    binding.heatmapEmptyMessage.isVisible = data.points.isEmpty()
                    renderHeatmap(data)
                    renderCaptureMarkers(data, latestAves)
                }
            }
        }
    }

    private fun collectAves() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.aves.collect { aves ->
                    latestAves = aves
                    val labels = buildList {
                        add(getString(R.string.heatmap_filter_all))
                        addAll(aves.map { it.nombreComun.ifBlank { it.titulo } })
                    }
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
                    (binding.heatmapFilterInput as AutoCompleteTextView).setAdapter(adapter)
                    if (binding.heatmapFilterInput.text.isNullOrBlank()) {
                        binding.heatmapFilterInput.setText(getString(R.string.heatmap_filter_all), false)
                    }
                    renderCaptureMarkers(latestHeatmap, latestAves)
                }
            }
        }
    }

    private fun renderHeatmap(data: HeatmapData) {
        val map = googleMap ?: return
        if (data.points.isEmpty()) {
            heatmapOverlay?.remove()
            heatmapOverlay = null
            heatmapProvider = null
            return
        }

        val provider = heatmapProvider
        if (provider == null) {
            val newProvider = HeatmapTileProvider.Builder()
                .data(data.points)
                .radius(HEATMAP_RADIUS)
                .build()
            heatmapProvider = newProvider
            heatmapOverlay = map.addTileOverlay(TileOverlayOptions().tileProvider(newProvider))
        } else {
            provider.setData(data.points)
            heatmapOverlay?.clearTileCache()
        }

        adjustCamera(map, data)
    }

    private fun renderCaptureMarkers(data: HeatmapData, aves: List<Ave>) {
        val map = googleMap ?: return
        captureMarkers.forEach { it.remove() }
        captureMarkers.clear()
        if (data.captures.isEmpty()) return

        val iconGenerator = createLabelIconGenerator()

        val demoAves = aves.take(3)
        data.captures.forEach { record ->
            val position = record.toLatLngWithDemoOffset(demoAves) ?: return@forEach
            val label = resolveAveLabel(record, aves, demoAves) ?: getString(R.string.heatmap_label_unknown)
            val subtitle = formatCoordinates(position)
            val labelIcon = iconGenerator.makeIcon(label)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(label)
                    .snippet(subtitle)
                    .icon(BitmapDescriptorFactory.fromBitmap(labelIcon))
                    .anchor(0.5f, 1.0f)
                    .zIndex(1f)
            )
            if (marker != null) {
                captureMarkers.add(marker)
            }
        }
    }

    private fun adjustCamera(map: GoogleMap, data: HeatmapData) {
        val builder = LatLngBounds.Builder()
        var hasBounds = false
        data.points.forEach { point ->
            builder.include(point)
            hasBounds = true
        }
        if (hasBounds) {
            map.setOnMapLoadedCallback {
                val bounds = builder.build()
                val center = bounds.center
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(center, DEFAULT_FOCUS_ZOOM))
            }
        }
    }

    override fun onDestroyView() {
        heatmapOverlay = null
        heatmapProvider = null
        latestHeatmap = HeatmapData(emptyList(), emptyList())
        captureMarkers.clear()
        latestAves = emptyList()
        googleMap = null
        _binding = null
        super.onDestroyView()
    }

    private fun resolveAveLabel(record: MediaRecord, aves: List<Ave>, demoAves: List<Ave>): String? {
        val effectiveAveId = record.aveId ?: demoAves.getOrNull((record.id % demoAves.size).toInt())?.id?.toLong()
        val ave = effectiveAveId?.let { id -> aves.firstOrNull { it.id.toLong() == id } }
        return ave?.nombreComun?.ifBlank { ave.titulo }
    }

    private fun MediaRecord.toLatLngWithDemoOffset(demoAves: List<Ave>): LatLng? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        if (aveId != null || demoAves.isEmpty()) {
            return LatLng(lat, lng)
        }
        val index = (id % demoAves.size).toInt()
        val offset = demoOffsets().getOrNull(index) ?: (0.0 to 0.0)
        return LatLng(lat + offset.first, lng + offset.second)
    }

    private fun demoOffsets(): List<Pair<Double, Double>> = listOf(
        0.03 to 0.03,
        -0.03 to 0.02,
        0.02 to -0.03
    )

    private fun formatCoordinates(position: LatLng): String {
        val lat = String.format(Locale.US, "%.4f", position.latitude)
        val lng = String.format(Locale.US, "%.4f", position.longitude)
        return getString(R.string.heatmap_label_coordinates, lat, lng)
    }

    private fun createLabelIconGenerator(): IconGenerator {
        val context = requireContext()
        val generator = IconGenerator(context)
        generator.setTextAppearance(R.style.TextAppearance_Vinculacion_MapLabel)
        generator.setColor(ContextCompat.getColor(context, R.color.surface_container))
        val paddingH = resources.getDimensionPixelSize(R.dimen.map_label_padding_horizontal)
        val paddingV = resources.getDimensionPixelSize(R.dimen.map_label_padding_vertical)
        generator.setContentPadding(paddingH, paddingV, paddingH, paddingV)
        return generator
    }

    companion object {
        private const val HEATMAP_RADIUS = 30
        private const val CAMERA_PADDING = 80
        private const val MIN_ZOOM = 4.5f
        private const val DEFAULT_FOCUS_ZOOM = 9.5f
    }
}
