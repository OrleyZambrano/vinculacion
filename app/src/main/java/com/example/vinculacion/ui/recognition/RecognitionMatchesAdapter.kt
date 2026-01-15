package com.example.vinculacion.ui.recognition

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vinculacion.R
import com.example.vinculacion.data.recognition.RecognitionMatch
import com.example.vinculacion.data.recognition.RecognitionSource
import com.example.vinculacion.databinding.ItemRecognitionMatchBinding
import kotlin.math.roundToInt

class RecognitionMatchesAdapter :
    ListAdapter<RecognitionMatch, RecognitionMatchesAdapter.MatchViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemRecognitionMatchBinding.inflate(inflater, parent, false)
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MatchViewHolder(
        private val binding: ItemRecognitionMatchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(match: RecognitionMatch) {
            val context = binding.root.context
            binding.matchTitle.text = match.label.ifBlank {
                match.scientificName ?: context.getString(R.string.no_results)
            }
            val subtitleValue = match.scientificName ?: match.label
            val subtitleRes = if (match.source == RecognitionSource.AUDIO) {
                R.string.recognition_source_audio
            } else {
                R.string.recognition_source_image
            }
            binding.matchSubtitle.text = context.getString(subtitleRes, subtitleValue)

            val confidencePercent = (match.confidence * 100).roundToInt().coerceIn(0, 100)
            binding.matchConfidence.text = context.getString(R.string.recognition_confidence_value, confidencePercent)

            val iconRes = if (match.source == RecognitionSource.AUDIO) R.drawable.ic_audio else R.drawable.ic_camera
            binding.matchIcon.setImageResource(iconRes)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RecognitionMatch>() {
        override fun areItemsTheSame(oldItem: RecognitionMatch, newItem: RecognitionMatch): Boolean {
            return oldItem.source == newItem.source && oldItem.label == newItem.label && oldItem.scientificName == newItem.scientificName
        }

        override fun areContentsTheSame(oldItem: RecognitionMatch, newItem: RecognitionMatch): Boolean {
            return oldItem == newItem
        }
    }
}
