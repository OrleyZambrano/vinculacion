package com.example.vinculacion.ui.routes

import android.graphics.drawable.ColorDrawable
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.vinculacion.R
import com.example.vinculacion.databinding.DialogRouteCreateFlowBinding
import com.example.vinculacion.ui.map.MapsViewModel
import com.example.vinculacion.ui.map.RouteParser
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RouteCreateFlowDialogFragment : DialogFragment() {

    private var _binding: DialogRouteCreateFlowBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapsViewModel by activityViewModels()

    private var googleMap: GoogleMap? = null
    private var step: Step = Step.SELECT_START

    private var tempPoint: LatLng? = null
    private var startPoint: LatLng? = null
    private var endPoint: LatLng? = null

    private val pathPoints = mutableListOf<LatLng>()
    private var pathPolyline: Polyline? = null

    private var tempMarker: Marker? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRouteCreateFlowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupActions()
        updateStepUi()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        @Suppress("DEPRECATION")
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.routeFlowMapContainer) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also { fragment ->
                childFragmentManager.beginTransaction()
                    .replace(R.id.routeFlowMapContainer, fragment)
                    .commitNow()
            }
        mapFragment.getMapAsync { map ->
            googleMap = map.apply {
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isMapToolbarEnabled = false
            }
            map.setOnMapClickListener { latLng ->
                when (step) {
                    Step.SELECT_START -> setTempPoint(latLng)
                    Step.TRACE -> addPathPoint(latLng)
                    Step.NAME -> Unit
                }
            }
        }
    }

    private fun setupActions() {
        binding.routeFlowCancel.setOnClickListener { dismiss() }
        binding.routeFlowConfirm.setOnClickListener { handleConfirm() }
        binding.routeFlowSearchButton.setOnClickListener { searchPlace() }
    }

    private fun updateStepUi() {
        when (step) {
            Step.SELECT_START -> {
                binding.routeFlowStep.text = getString(R.string.route_flow_step_start)
                binding.routeFlowHint.text = getString(R.string.route_flow_hint_start)
                binding.routeFlowConfirm.text = getString(R.string.route_flow_confirm_start)
                binding.inputLayoutRouteName.visibility = View.GONE
            }
            Step.TRACE -> {
                binding.routeFlowStep.text = getString(R.string.route_flow_step_trace)
                binding.routeFlowHint.text = getString(R.string.route_flow_hint_trace)
                binding.routeFlowConfirm.text = getString(R.string.route_flow_confirm_end)
                binding.inputLayoutRouteName.visibility = View.GONE
            }
            Step.NAME -> {
                binding.routeFlowStep.text = getString(R.string.route_flow_step_name)
                binding.routeFlowHint.text = getString(R.string.route_flow_hint_name)
                binding.routeFlowConfirm.text = getString(R.string.route_flow_confirm_save)
                binding.inputLayoutRouteName.visibility = View.VISIBLE
            }
        }
    }

    private fun handleConfirm() {
        when (step) {
            Step.SELECT_START -> confirmStart()
            Step.TRACE -> confirmEnd()
            Step.NAME -> saveRoute()
        }
    }

    private fun confirmStart() {
        val point = tempPoint ?: run {
            Snackbar.make(binding.root, getString(R.string.route_flow_select_point), Snackbar.LENGTH_SHORT).show()
            return
        }
        startPoint = point
        pathPoints.clear()
        pathPoints.add(point)
        startMarker?.remove()
        startMarker = addMarker(point, R.string.route_point_a)
        clearTempSelection()
        redrawPath()
        step = Step.TRACE
        updateStepUi()
    }

    private fun confirmEnd() {
        val point = tempPoint ?: run {
            Snackbar.make(binding.root, getString(R.string.route_flow_select_point), Snackbar.LENGTH_SHORT).show()
            return
        }
        endPoint = point
        if (pathPoints.lastOrNull() != point) {
            pathPoints.add(point)
        }
        endMarker?.remove()
        endMarker = addMarker(point, R.string.route_point_b)
        clearTempSelection()
        redrawPath()
        step = Step.NAME
        updateStepUi()
    }

    private fun saveRoute() {
        val title = binding.inputRouteName.text?.toString()?.trim().orEmpty()
        if (title.isBlank()) {
            Snackbar.make(binding.root, getString(R.string.tour_route_name_hint), Snackbar.LENGTH_SHORT).show()
            return
        }
        if (pathPoints.size < 2) {
            Snackbar.make(binding.root, getString(R.string.route_flow_missing_points), Snackbar.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val geoJson = RouteParser.buildLineString(pathPoints)
            val result = viewModel.createRoute(title, geoJson)
            if (result.isSuccess) {
                Snackbar.make(binding.root, getString(R.string.tour_route_create_success), Snackbar.LENGTH_SHORT).show()
                dismiss()
            } else {
                Snackbar.make(binding.root, getString(R.string.tour_route_create_error), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setTempPoint(point: LatLng) {
        tempPoint = point
        tempMarker?.remove()
        tempMarker = googleMap?.addMarker(
            MarkerOptions()
                .position(point)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
    }

    private fun addPathPoint(point: LatLng) {
        tempPoint = point
        if (pathPoints.isEmpty()) {
            pathPoints.add(point)
        } else if (pathPoints.lastOrNull() != point) {
            pathPoints.add(point)
        }
        redrawPath()
    }

    private fun redrawPath() {
        val map = googleMap ?: return
        pathPolyline?.remove()
        if (pathPoints.size < 2) return
        pathPolyline = map.addPolyline(
            PolylineOptions()
                .addAll(pathPoints)
                .color(requireContext().getColor(R.color.primary_color))
                .width(POLYLINE_WIDTH)
                .geodesic(true)
        )
    }

    private fun clearTempSelection() {
        tempPoint = null
        tempMarker?.remove()
        tempMarker = null
    }

    private fun addMarker(point: LatLng, labelRes: Int): Marker? {
        return googleMap?.addMarker(
            MarkerOptions()
                .position(point)
                .title(getString(labelRes))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
    }

    private fun searchPlace() {
        val query = binding.inputPlaceSearch.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 1)
                } catch (exception: Exception) {
                    null
                }
            }
            val address = result?.firstOrNull()
            if (address == null) {
                Snackbar.make(binding.root, getString(R.string.route_flow_search_empty), Snackbar.LENGTH_SHORT).show()
                return@launch
            }
            val point = LatLng(address.latitude, address.longitude)
            setTempPoint(point)
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, DEFAULT_ZOOM))
        }
    }

    override fun onDestroyView() {
        googleMap = null
        pathPolyline = null
        pathPoints.clear()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val DEFAULT_ZOOM = 15f
        private const val POLYLINE_WIDTH = 8f

        fun newInstance(): RouteCreateFlowDialogFragment = RouteCreateFlowDialogFragment()
    }

    private enum class Step {
        SELECT_START,
        TRACE,
        NAME
    }
}
