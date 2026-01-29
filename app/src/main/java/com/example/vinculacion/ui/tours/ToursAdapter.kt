package com.example.vinculacion.ui.tours

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vinculacion.R
import com.example.vinculacion.data.local.room.entities.TourParticipantStatus
import com.example.vinculacion.data.local.room.entities.TourStatus
import com.example.vinculacion.databinding.ItemTourCardBinding
import java.text.DateFormat
import java.util.Date

class ToursAdapter(
    private val onPrimaryAction: (TourCardUi) -> Unit,
    private val onSecondaryAction: (TourCardUi) -> Unit,
    private val onWhatsAppAction: (TourCardUi) -> Unit = {},
    private val onRouteAction: (TourCardUi) -> Unit = {}
) : ListAdapter<TourCardUi, ToursAdapter.TourViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TourViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTourCardBinding.inflate(inflater, parent, false)
        return TourViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TourViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TourViewHolder(private val binding: ItemTourCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TourCardUi) {
            val context = binding.root.context
            binding.tourTitle.text = item.tour.title
            binding.tourGuideName.text = context.getString(
                R.string.tour_label_guide,
                item.tour.guideName ?: context.getString(R.string.nav_profile)
            )
            binding.tourSchedule.text = formatSchedule(item.tour.startTimeEpoch, item.tour.endTimeEpoch)
            binding.tourStatusChip.text = statusLabel(context, item.tour.status)
            binding.tourCapacity.text = capacityLabel(context, item.tour.capacity, item.capacityRemaining)

            val joinStatusText = when (item.joinStatus) {
                TourParticipantStatus.PENDING -> context.getString(R.string.tour_status_pending)
                TourParticipantStatus.APPROVED -> context.getString(R.string.tour_status_approved)
                TourParticipantStatus.DECLINED -> context.getString(R.string.tour_status_declined)
                TourParticipantStatus.CANCELLED -> context.getString(R.string.tour_status_cancelled)
                null -> null
            }
            binding.tourRequestStatus.apply {
                val textToShow = when {
                    joinStatusText != null -> joinStatusText
                    item.isGuide -> context.getString(R.string.tour_role_badge)
                    item.requiresAuthentication -> context.getString(R.string.tour_status_guest_prompt)
                    else -> null
                }
                visibility = if (textToShow != null) View.VISIBLE else View.GONE
                text = textToShow
            }

            binding.tourSecondaryAction.apply {
                visibility = View.GONE
            }

            binding.tourPrimaryAction.apply {
                text = when {
                    item.isGuide -> context.getString(R.string.tour_manage_participants)
                    item.canCancelJoin -> context.getString(R.string.tour_action_cancel)
                    else -> context.getString(R.string.tour_action_request)
                }
                isEnabled = if (item.isGuide) true else item.canRequestJoin || item.canCancelJoin || item.requiresAuthentication
                setOnClickListener { onPrimaryAction(item) }
            }

            binding.tourRouteAction.apply {
                val hasRoute = !item.tour.routeGeoJson.isNullOrBlank()
                visibility = if (hasRoute) View.VISIBLE else View.GONE
                setOnClickListener { onRouteAction(item) }
            }
            
            // Configurar información del guía
            binding.tourGuideContactName.text = context.getString(R.string.tour_guide_name, item.tour.guideName ?: "Sin nombre")
            
            // Mostrar teléfono si está disponible
            val guidePhone = item.tour.guidePhone
            if (!guidePhone.isNullOrBlank()) {
                binding.tourGuidePhone.visibility = View.VISIBLE
                binding.tourGuidePhone.text = context.getString(R.string.tour_guide_phone, guidePhone)
                binding.tourWhatsAppButton.visibility = View.VISIBLE
                binding.tourWhatsAppButton.setOnClickListener { onWhatsAppAction(item) }
            } else {
                binding.tourGuidePhone.visibility = View.GONE
                binding.tourWhatsAppButton.visibility = View.GONE
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<TourCardUi>() {
            override fun areItemsTheSame(oldItem: TourCardUi, newItem: TourCardUi): Boolean =
                oldItem.tour.id == newItem.tour.id

            override fun areContentsTheSame(oldItem: TourCardUi, newItem: TourCardUi): Boolean =
                oldItem == newItem
        }

        private fun statusLabel(context: android.content.Context, status: TourStatus): String = when (status) {
            TourStatus.PUBLISHED -> context.getString(R.string.tour_status_published)
            TourStatus.IN_PROGRESS -> context.getString(R.string.tour_status_in_progress)
            TourStatus.COMPLETED -> context.getString(R.string.tour_status_completed)
            TourStatus.CANCELLED -> context.getString(R.string.tour_status_cancelled_label)
            TourStatus.DRAFT -> context.getString(R.string.tour_status_draft_label)
        }

        private fun capacityLabel(context: android.content.Context, capacity: Int?, remaining: Int?): String = when {
            capacity == null -> context.getString(R.string.tour_capacity_unlimited)
            remaining == null -> context.getString(R.string.tour_capacity_value, capacity)
            else -> context.getString(R.string.tour_capacity_remaining, remaining, capacity)
        }

        private fun formatSchedule(start: Long, end: Long?): String {
            val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            val startDate = formatter.format(Date(start))
            val endDate = end?.let { formatter.format(Date(it)) }
            return if (endDate != null) "$startDate - $endDate" else startDate
        }
    }
}
