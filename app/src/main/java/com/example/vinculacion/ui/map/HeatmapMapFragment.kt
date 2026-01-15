package com.example.vinculacion.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vinculacion.R
import com.example.vinculacion.databinding.FragmentHeatmapMapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.maps.android.heatmaps.HeatmapTileProvider
import kotlinx.coroutines.launch

class HeatmapMapFragment : Fragment() {

    private var _binding: FragmentHeatmapMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapsViewModel by activityViewModels()

    private var googleMap: GoogleMap? = null
    private var heatmapProvider: HeatmapTileProvider? = null
    private var heatmapOverlay: TileOverlay? = null
    private var latestHeatmap: HeatmapData = HeatmapData(emptyList(), emptyList())

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
        collectHeatmap()
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.heatmapContainer) as? SupportMapFragment
            ?: return
        mapFragment.getMapAsync { map ->
            googleMap = map.apply {
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isMapToolbarEnabled = false
            }
            renderHeatmap(latestHeatmap)
        }
    }

    private fun collectHeatmap() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.heatmapData.collect { data ->
                    latestHeatmap = data
                    binding.heatmapEmptyMessage.isVisible = data.points.isEmpty()
                    renderHeatmap(data)
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

    private fun adjustCamera(map: GoogleMap, data: HeatmapData) {
        val builder = LatLngBounds.Builder()
        var hasBounds = false
        data.points.forEach { point ->
            builder.include(point)
            hasBounds = true
        }
        if (hasBounds) {
            map.setOnMapLoadedCallback {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), CAMERA_PADDING))
            }
        }
    }

    override fun onDestroyView() {
        heatmapOverlay = null
        heatmapProvider = null
        latestHeatmap = HeatmapData(emptyList(), emptyList())
        googleMap = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val HEATMAP_RADIUS = 30
        private const val CAMERA_PADDING = 80
    }
}
