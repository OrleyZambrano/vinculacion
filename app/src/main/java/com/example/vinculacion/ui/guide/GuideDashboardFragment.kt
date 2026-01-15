package com.example.vinculacion.ui.guide

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vinculacion.R
import com.example.vinculacion.databinding.FragmentGuideDashboardBinding
import com.example.vinculacion.ui.common.UiState
import com.example.vinculacion.ui.tours.ToursAdapter
import com.example.vinculacion.ui.tours.TourParticipantsBottomSheet
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GuideDashboardFragment : Fragment() {

    private var _binding: FragmentGuideDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GuideDashboardViewModel by viewModels()

    private val myToursAdapter = ToursAdapter(
        onPrimaryAction = { item ->
            // Para guías: gestionar el tour (editar, cancelar, etc.)
            showTourManagementOptions(item.tour)
        },
        onSecondaryAction = { item ->
            // Ver participantes
            TourParticipantsBottomSheet.newInstance(item.tour.id, item.tour.title, item.tour.guideId)
                .show(childFragmentManager, "tourParticipants")
        },
        onWhatsAppAction = { item ->
            // Los guías no necesitan contactarse a sí mismos
            Snackbar.make(binding.root, "Este es tu tour", Snackbar.LENGTH_SHORT).show()
        }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGuideDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        collectUiState()
        collectEvents()
        
        viewModel.loadMyTours()
    }

    private fun setupViews() {
        binding.myToursRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = myToursAdapter
        }

        binding.createTourFab.setOnClickListener {
            showCreateTourDialog()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.swipeRefresh.isRefreshing = false
                binding.loadingIndicator.isVisible = state is UiState.Loading

                when (state) {
                    is UiState.Loading -> {
                        binding.emptyState.isVisible = false
                        binding.myToursRecyclerView.isVisible = false
                    }
                    is UiState.Success -> {
                        binding.emptyState.isVisible = state.data.isEmpty()
                        binding.myToursRecyclerView.isVisible = state.data.isNotEmpty()
                        myToursAdapter.submitList(state.data)
                        updateStats(state.data)
                    }
                    is UiState.Empty -> {
                        binding.emptyState.isVisible = true
                        binding.myToursRecyclerView.isVisible = false
                        myToursAdapter.submitList(emptyList())
                    }
                    is UiState.Error -> {
                        showError(state.message.toString())
                    }
                }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is GuideDashboardEvent.ShowError -> showError(event.message)
                    is GuideDashboardEvent.ShowSuccess -> showSuccess(event.message)
                    is GuideDashboardEvent.NavigateToTourDetail -> {
                        // TODO: Navigate to tour detail
                    }
                }
            }
        }
    }

    private fun updateStats(tours: List<com.example.vinculacion.ui.tours.TourCardUi>) {
        val activeTours = tours.count { it.tour.status == com.example.vinculacion.data.local.room.entities.TourStatus.PUBLISHED }
        val totalParticipants: Int = tours
            .sumOf { _: com.example.vinculacion.ui.tours.TourCardUi ->
                // TODO: Reemplazar con el conteo real de participantes
                0L
            }
            .toInt()
        
        binding.statsActiveTours.text = getString(R.string.stats_active_tours, activeTours)
        binding.statsTotalParticipants.text = getString(R.string.stats_total_participants, totalParticipants)
    }

    private fun showTourManagementOptions(tour: com.example.vinculacion.data.model.Tour) {
        // TODO: Show dialog with tour management options (edit, cancel, view participants)
    }

    private fun showCreateTourDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_tour, null)

        val inputTitle = dialogView.findViewById<TextInputEditText>(R.id.inputTourTitle)
        val inputDescription = dialogView.findViewById<TextInputEditText>(R.id.inputTourDescription)
        val inputDate = dialogView.findViewById<TextInputEditText>(R.id.inputTourDate)
        val inputLocation = dialogView.findViewById<TextInputEditText>(R.id.inputTourLocation)
        val inputCapacity = dialogView.findViewById<TextInputEditText>(R.id.inputTourCapacity)
        val inputPhone = dialogView.findViewById<TextInputEditText>(R.id.inputGuidePhone)
        val createButton = dialogView.findViewById<MaterialButton>(R.id.tourCreateButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.tourCreateCancelButton)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

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

        createButton.setOnClickListener {
            val title = inputTitle.text?.toString()?.trim()
            val description = inputDescription.text?.toString()?.trim()
            val dateStr = inputDate.text?.toString()?.trim()
            val location = inputLocation.text?.toString()?.trim()
            val capacityStr = inputCapacity.text?.toString()?.trim()
            val phone = inputPhone.text?.toString()?.trim()

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

            try {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val dateTime = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()

                viewModel.createTour(
                    title = title,
                    description = description,
                    dateTime = dateTime,
                    meetingPoint = location,
                    capacity = capacity,
                    guidePhone = phone
                )
                dialog.dismiss()
            } catch (e: Exception) {
                showError("Error en la fecha seleccionada")
            }
        }

        cancelButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = GuideDashboardFragment()
    }
}