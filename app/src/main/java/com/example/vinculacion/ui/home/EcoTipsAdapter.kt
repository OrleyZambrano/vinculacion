package com.example.vinculacion.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vinculacion.databinding.ItemEcoTipBinding

data class EcoTip(
    val emoji: String,
    val text: String
)

class EcoTipsAdapter : RecyclerView.Adapter<EcoTipsAdapter.EcoTipViewHolder>() {

    private val tips = listOf(
        EcoTip("🌿", "Mantén una distancia segura y respeta el hábitat natural de las aves"),
        EcoTip("🔇", "Evita hacer ruidos fuertes que puedan perturbar a las aves"),
        EcoTip("📸", "Fotografía sin flash y desde una distancia apropiada"),
        EcoTip("♻️", "No dejes basura, llévala contigo para mantener el ecosistema limpio"),
        EcoTip("🦅", "Usa binoculares en lugar de acercarte demasiado para observar"),
        EcoTip("👣", "Mantente en los senderos marcados para proteger la vegetación")
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EcoTipViewHolder {
        val binding = ItemEcoTipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EcoTipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EcoTipViewHolder, position: Int) {
        holder.bind(tips[position])
    }

    override fun getItemCount(): Int = tips.size

    class EcoTipViewHolder(
        private val binding: ItemEcoTipBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tip: EcoTip) {
            binding.tipEmoji.text = tip.emoji
            binding.tipText.text = tip.text
        }
    }
}
