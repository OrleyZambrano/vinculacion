package com.example.vinculacion.ui.routes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vinculacion.data.model.GuideRoute
import com.example.vinculacion.databinding.ItemMyRouteBinding

class MyRoutesAdapter(
    private val onEdit: (GuideRoute) -> Unit,
    private val onDelete: (GuideRoute) -> Unit
) : ListAdapter<GuideRoute, MyRoutesAdapter.RouteViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ItemMyRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RouteViewHolder(binding, onEdit, onDelete)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RouteViewHolder(
        private val binding: ItemMyRouteBinding,
        private val onEdit: (GuideRoute) -> Unit,
        private val onDelete: (GuideRoute) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(route: GuideRoute) {
            binding.routeTitle.text = route.title
            binding.routeEdit.setOnClickListener { onEdit(route) }
            binding.routeDelete.setOnClickListener { onDelete(route) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<GuideRoute>() {
        override fun areItemsTheSame(oldItem: GuideRoute, newItem: GuideRoute): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: GuideRoute, newItem: GuideRoute): Boolean =
            oldItem == newItem
    }
}
