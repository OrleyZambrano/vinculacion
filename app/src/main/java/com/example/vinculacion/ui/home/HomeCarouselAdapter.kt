package com.example.vinculacion.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vinculacion.R
import com.example.vinculacion.data.model.Ave
import com.example.vinculacion.databinding.ItemHomeCarouselBinding

class HomeCarouselAdapter(
    private val onItemClick: (Ave) -> Unit
) : RecyclerView.Adapter<HomeCarouselAdapter.CarouselViewHolder>() {

    private val items = mutableListOf<Ave>()
    private val infiniteItems = mutableListOf<Ave>()

    fun submitList(newItems: List<Ave>) {
        items.clear()
        items.addAll(newItems)
        
        // Crear lista infinita triplicando los items
        infiniteItems.clear()
        if (items.isNotEmpty()) {
            infiniteItems.addAll(items)
            infiniteItems.addAll(items)
            infiniteItems.addAll(items)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
        val binding = ItemHomeCarouselBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CarouselViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
        val actualPosition = position % items.size
        holder.bind(items[actualPosition])
    }

    override fun getItemCount(): Int = if (items.isEmpty()) 0 else Int.MAX_VALUE

    class CarouselViewHolder(
        private val binding: ItemHomeCarouselBinding,
        private val onItemClick: (Ave) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(ave: Ave) {
            binding.carouselTitle.text = ave.titulo
            binding.carouselSubtitle.text = ave.nombreCientifico
            Glide.with(binding.carouselImage)
                .load(ave.imageUrl())
                .placeholder(R.drawable.image_placeholder)
                .centerCrop()
                .into(binding.carouselImage)
            binding.root.setOnClickListener { onItemClick(ave) }
        }
    }
}
