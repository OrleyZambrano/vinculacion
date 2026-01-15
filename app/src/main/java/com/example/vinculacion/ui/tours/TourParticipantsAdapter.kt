package com.example.vinculacion.ui.tours

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vinculacion.R
import com.example.vinculacion.data.local.room.entities.TourParticipantStatus
import com.example.vinculacion.data.model.TourParticipant
import com.example.vinculacion.databinding.ItemTourParticipantBinding

class TourParticipantsAdapter(
    private val onApprove: (TourParticipant) -> Unit,
    private val onDecline: (TourParticipant) -> Unit,
    private val onContactParticipant: (TourParticipant) -> Unit = {}
) : ListAdapter<TourParticipant, TourParticipantsAdapter.ParticipantViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val binding = ItemTourParticipantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ParticipantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ParticipantViewHolder(private val binding: ItemTourParticipantBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TourParticipant) {
            val context = binding.root.context
            binding.participantName.text = item.userName.ifBlank { context.getString(R.string.nav_profile) }
            val contact = listOf(item.userEmail, item.userPhone).filter { !it.isNullOrBlank() }.joinToString(" · ")
            binding.participantContact.text = contact.ifBlank { context.getString(R.string.tour_guide_phone, "No hay contacto") }
            
            // Status and actions
            binding.participantStatus.text = when (item.status) {
                TourParticipantStatus.PENDING -> context.getString(R.string.tour_status_pending)
                TourParticipantStatus.APPROVED -> context.getString(R.string.tour_status_approved) 
                TourParticipantStatus.DECLINED -> context.getString(R.string.tour_status_declined)
                TourParticipantStatus.CANCELLED -> context.getString(R.string.tour_status_cancelled)
            }
            
            val showActions = item.status == TourParticipantStatus.PENDING
            binding.participantApprove.apply {
                visibility = if (showActions) View.VISIBLE else View.GONE
                setOnClickListener { onApprove(item) }
            }
            binding.participantDecline.apply {
                visibility = if (showActions) View.VISIBLE else View.GONE
                setOnClickListener { onDecline(item) }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<TourParticipant>() {
            override fun areItemsTheSame(oldItem: TourParticipant, newItem: TourParticipant): Boolean =
                oldItem.tourId == newItem.tourId && oldItem.userId == newItem.userId

            override fun areContentsTheSame(oldItem: TourParticipant, newItem: TourParticipant): Boolean =
                oldItem == newItem
        }
    }
}
