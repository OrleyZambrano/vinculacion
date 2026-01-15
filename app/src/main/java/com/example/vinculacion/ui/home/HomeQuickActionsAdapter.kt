package com.example.vinculacion.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vinculacion.databinding.ItemHomeQuickActionBinding

class HomeQuickActionsAdapter(
    private val onActionClick: (HomeAction) -> Unit
) : RecyclerView.Adapter<HomeQuickActionsAdapter.ActionViewHolder>() {

    private val items = mutableListOf<HomeQuickAction>()

    fun submitList(newItems: List<HomeQuickAction>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val binding = ItemHomeQuickActionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ActionViewHolder(binding, onActionClick)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ActionViewHolder(
        private val binding: ItemHomeQuickActionBinding,
        private val onActionClick: (HomeAction) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeQuickAction) {
            binding.actionIcon.setImageResource(item.iconRes)
            binding.actionTitle.text = item.title
            binding.actionSubtitle.text = item.subtitle
            binding.root.setOnClickListener { onActionClick(item.action) }
        }
    }
}
