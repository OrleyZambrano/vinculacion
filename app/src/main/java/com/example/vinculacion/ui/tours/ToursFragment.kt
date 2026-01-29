package com.example.vinculacion.ui.tours

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vinculacion.R
import com.example.vinculacion.MainActivity
import com.example.vinculacion.data.model.GuideRoute
import com.example.vinculacion.data.model.UserProfile
import com.example.vinculacion.databinding.FragmentToursBinding
import com.example.vinculacion.data.repository.RoutesRepository
import com.example.vinculacion.ui.map.MapsViewModel
import com.example.vinculacion.ui.common.UiState
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ToursFragment : Fragment() {

    private var _binding: FragmentToursBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ToursViewModel by viewModels()
    private val mapsViewModel: MapsViewModel by activityViewModels()
    private val routesRepository by lazy { RoutesRepository(requireContext()) }
    
    private var currentSearchQuery = ""
    private var selectedFilters = mutableSetOf<String>()

    private val toursAdapter = ToursAdapter(
        onPrimaryAction = { item ->
            if (item.canCancelJoin) {
                viewModel.cancelJoin(item.tour)
            } else {
                viewModel.requestJoin(item.tour)
            }
        },
        onSecondaryAction = { item ->
            if (item.isGuide) {
                TourParticipantsBottomSheet.newInstance(item.tour.id, item.tour.title, item.tour.guideId)
                    .show(childFragmentManager, "tourParticipants")
            }
        },
        onWhatsAppAction = { item ->
            openWhatsApp(item.tour)
        },
        onRouteAction = { item ->
            mapsViewModel.setSelectedRouteGeoJson(item.tour.routeGeoJson)
            (activity as? MainActivity)?.openMap()
        }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToursBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearchAndFilters()
        setupSwipeRefresh()
        setupCreateTourFab()
        
        collectUiState()
        collectEvents()
        observeAuthState()
    }
    
    private fun setupRecyclerView() {
        binding.toursRecyclerView.adapter = toursAdapter
    }
    
    private fun setupSearchAndFilters() {
        // Configurar búsqueda
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString() ?: ""
                applyFiltersAndSearch()
            }
        })
        
        // Configurar filtros
        binding.chipAllTours.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedFilters.clear()
                selectedFilters.add("all")
                uncheckOtherFilters(binding.chipAllTours.id)
            }
            applyFiltersAndSearch()
        }
        
        binding.chipMyTours.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedFilters.add("my_tours")
                binding.chipAllTours.isChecked = false
            } else {
                selectedFilters.remove("my_tours")
            }
            applyFiltersAndSearch()
        }
        
        binding.chipAvailableTours.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedFilters.add("available")
                binding.chipAllTours.isChecked = false
            } else {
                selectedFilters.remove("available")
            }
            applyFiltersAndSearch()
        }
        
        // Inicializar con "Todos" seleccionado
        selectedFilters.add("all")
    }
    
    private fun uncheckOtherFilters(exceptId: Int) {
        if (exceptId != binding.chipMyTours.id) binding.chipMyTours.isChecked = false
        if (exceptId != binding.chipAvailableTours.id) binding.chipAvailableTours.isChecked = false
    }
    
    private fun applyFiltersAndSearch() {
        viewModel.applySearchAndFilters(currentSearchQuery, selectedFilters.toSet())
    }
    
    private fun setupSwipeRefresh() {
        binding.toursSwipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }
    
    private fun setupCreateTourFab() {
        binding.toursAddFab.setOnClickListener {
            showCreateTourDialog()
        }
    }
    
    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { authState ->
                    // Mostrar FAB solo para guías
                    binding.toursAddFab.isVisible = authState.profile.role.canManageTours()
                }
            }
        }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.toursProgress.isVisible = state is UiState.Loading
                    when (state) {
                        UiState.Loading -> Unit
                        is UiState.Empty -> showEmptyState(true)
                        is UiState.Success -> {
                            showEmptyState(false)
                            toursAdapter.submitList(state.data)
                        }
                        is UiState.Error -> {
                            showEmptyState(true)
                            showError(state.message?.toString() ?: getString(R.string.tour_error_generic))
                        }
                    }
                }
            }
        }
    }

    private fun showEmptyState(visible: Boolean) {
        binding.toursEmptyState.isVisible = visible
        binding.toursRecyclerView.isVisible = !visible
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is TourEvent.ShowMessage -> showMessage(event.message)
                        is TourEvent.ShowError -> showError(event.message)
                    }
                }
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).setBackgroundTint(resources.getColor(R.color.accent_color, null)).show()
    }
    
    private fun showCreateTourDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_tour, null)
        
        val inputTitle = dialogView.findViewById<TextInputEditText>(R.id.inputTourTitle)
        val inputDescription = dialogView.findViewById<TextInputEditText>(R.id.inputTourDescription)
        val inputDate = dialogView.findViewById<TextInputEditText>(R.id.inputTourDate)
        val inputLocation = dialogView.findViewById<TextInputEditText>(R.id.inputTourLocation)
        val inputRoute = dialogView.findViewById<AutoCompleteTextView>(R.id.inputTourRoute)
        val inputCapacity = dialogView.findViewById<TextInputEditText>(R.id.inputTourCapacity)
        val inputPhone = dialogView.findViewById<TextInputEditText>(R.id.inputGuidePhone)
        val createButton = dialogView.findViewById<MaterialButton>(R.id.tourCreateButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.tourCreateCancelButton)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
            
        // Configurar selector de fecha
        inputDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val timePickerDialog = TimePickerDialog(
                        requireContext(),
                        { _, hourOfDay, minute ->
                            calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            inputDate.setText(dateFormat.format(calendar.time))
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                    )
                    timePickerDialog.show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.datePicker.minDate = System.currentTimeMillis()
            datePickerDialog.show()
        }

        var selectedRoute: GuideRoute? = null
        viewLifecycleOwner.lifecycleScope.launch {
            val guideId = viewModel.authState.value.profile.id
            routesRepository.syncFromRemote()
            val routes = routesRepository.getRoutesByGuide(guideId)
            val labels = listOf(getString(R.string.tour_create_route_none)) + routes.map { it.title }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
            inputRoute.setAdapter(adapter)
            inputRoute.setText(getString(R.string.tour_create_route_none), false)
            inputRoute.setOnItemClickListener { parent, _, position, _ ->
                val label = parent.getItemAtPosition(position) as String
                selectedRoute = routes.firstOrNull { it.title == label }
            }
        }
        
        createButton.setOnClickListener {
            val title = inputTitle.text?.toString()?.trim()
            val description = inputDescription.text?.toString()?.trim()
            val dateStr = inputDate.text?.toString()?.trim()
            val location = inputLocation.text?.toString()?.trim()
            val capacityStr = inputCapacity.text?.toString()?.trim()
            val phone = inputPhone.text?.toString()?.trim()
            
            // Validaciones
            if (title.isNullOrBlank()) {
                inputTitle.error = "El título es requerido"
                return@setOnClickListener
            }
            if (dateStr.isNullOrBlank()) {
                showError("Selecciona una fecha y hora")
                return@setOnClickListener
            }
            if (location.isNullOrBlank()) {
                showError("El punto de encuentro es requerido")
                return@setOnClickListener
            }
            
            val capacity = capacityStr?.toIntOrNull()
            if (capacity != null && capacity <= 0) {
                inputCapacity.error = "El cupo debe ser mayor a 0"
                return@setOnClickListener
            }
            
            // Crear tour
            try {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val dateTime = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                
                viewModel.createTour(
                    title = title,
                    description = description,
                    dateTime = dateTime,
                    meetingPoint = location,
                    capacity = capacity,
                    guidePhone = phone,
                    routeId = selectedRoute?.id,
                    routeGeoJson = selectedRoute?.geoJson
                )
                dialog.dismiss()
            } catch (e: Exception) {
                showError("Error en la fecha seleccionada")
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun openWhatsApp(tour: com.example.vinculacion.data.model.Tour) {
        val phone = tour.guidePhone
        if (phone.isNullOrBlank()) {
            showError("No hay teléfono disponible para contactar al guía")
            return
        }
        
        try {
            val message = getString(R.string.tour_whatsapp_message, tour.title)
            val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
            
            // Limpiar número de teléfono y asegurar formato internacional
            val cleanPhone = phone.replace(Regex("[^+\\d]"), "")
            val formattedPhone = if (cleanPhone.startsWith("+")) {
                cleanPhone
            } else {
                "+549$cleanPhone" // Argentina country code
            }
            
            // Intentar abrir WhatsApp nativo primero
            val whatsAppIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("whatsapp://send?phone=$formattedPhone&text=$encodedMessage")
                setPackage("com.whatsapp")
            }
            
            try {
                startActivity(whatsAppIntent)
            } catch (e: ActivityNotFoundException) {
                // Si WhatsApp no está instalado, usar web
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/$formattedPhone?text=$encodedMessage")
                }
                startActivity(webIntent)
            }
            
        } catch (e: Exception) {
            showError("Error al contactar al guía: ${e.message}")
        }
    }

    override fun onDestroyView() {
        binding.toursRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = ToursFragment()
    }
}
