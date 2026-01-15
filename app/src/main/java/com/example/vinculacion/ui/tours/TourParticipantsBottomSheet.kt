package com.example.vinculacion.ui.tours

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vinculacion.R
import com.example.vinculacion.data.local.room.entities.TourParticipantStatus
import com.example.vinculacion.databinding.BottomSheetTourParticipantsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class TourParticipantsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTourParticipantsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ToursViewModel by activityViewModels()

    private lateinit var participantsAdapter: TourParticipantsAdapter
    private var tourId: String = ""
    private var tourTitle: String = ""
    private var guideId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetTourParticipantsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tourId = requireArguments().getString(ARG_TOUR_ID).orEmpty()
        tourTitle = requireArguments().getString(ARG_TOUR_TITLE).orEmpty()
        guideId = requireArguments().getString(ARG_TOUR_GUIDE).orEmpty()
        participantsAdapter = TourParticipantsAdapter(
            onApprove = { participant -> 
                viewModel.approveParticipant(tourId, guideId, participant.userId)
                showSnackbar("Aprobado: ${participant.userName}")
            },
            onDecline = { participant -> 
                viewModel.declineParticipant(tourId, guideId, participant.userId) 
                showSnackbar("Rechazado: ${participant.userName}")
            },
            onContactParticipant = { participant ->
                contactParticipant(participant)
            }
        )
        binding.participantsRecyclerView.adapter = participantsAdapter
        binding.participantsTitle.text = tourTitle
        viewModel.refreshParticipants(tourId)
        collectParticipants()
    }

    private fun collectParticipants() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeParticipants(tourId).collect { participants ->
                    binding.participantsEmptyState.isVisible = participants.isEmpty()
                    participantsAdapter.submitList(participants)
                    
                    val approved = participants.count { it.status == TourParticipantStatus.APPROVED }
                    val pending = participants.count { it.status == TourParticipantStatus.PENDING }
                    val declined = participants.count { it.status == TourParticipantStatus.DECLINED }
                    val cancelled = participants.count { it.status == TourParticipantStatus.CANCELLED }

                    binding.participantsTitle.text = "$tourTitle"
                    binding.participantsSubtitle.text = "$approved aprobados · $pending pendientes · $declined rechazados · $cancelled cancelados"
                }
            }
        }
    }
    
    private fun contactParticipant(participant: com.example.vinculacion.data.model.TourParticipant) {
        if (participant.userPhone.isNotBlank()) {
            try {
                val message = getString(R.string.guide_contact_participant_message, tourTitle)
                val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
                val cleanPhone = participant.userPhone.replace(Regex("[^+\\d]"), "")
                val formattedPhone = if (cleanPhone.startsWith("+")) cleanPhone else "+549$cleanPhone"
                
                val whatsAppIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("whatsapp://send?phone=$formattedPhone&text=$encodedMessage")
                    setPackage("com.whatsapp")
                }
                
                try {
                    startActivity(whatsAppIntent)
                } catch (e: Exception) {
                    val webIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://wa.me/$formattedPhone?text=$encodedMessage")
                    }
                    startActivity(webIntent)
                }
            } catch (e: Exception) {
                showSnackbar("Error al contactar participante")
            }
        } else {
            showSnackbar("No hay teléfono disponible")
        }
    }
    
    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        binding.participantsRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TOUR_ID = "arg_tour_id"
        private const val ARG_TOUR_TITLE = "arg_tour_title"
        private const val ARG_TOUR_GUIDE = "arg_tour_guide"

        fun newInstance(tourId: String, title: String, guideId: String): TourParticipantsBottomSheet {
            return TourParticipantsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TOUR_ID, tourId)
                    putString(ARG_TOUR_TITLE, title)
                    putString(ARG_TOUR_GUIDE, guideId)
                }
            }
        }
    }
}
