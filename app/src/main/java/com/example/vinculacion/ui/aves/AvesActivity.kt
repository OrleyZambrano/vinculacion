package com.example.vinculacion.ui.aves

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.vinculacion.data.model.Ave
import com.example.vinculacion.databinding.ActivityAvesBinding
import com.example.vinculacion.databinding.ItemAveBinding
import com.example.vinculacion.ui.common.UiState

class AvesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAvesBinding
    private val viewModel: AvesViewModel by viewModels()
    private val avesAdapter = AvesAdapter()
    private val searchAdapter = AvesAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAvesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        setupSearch()
        setupObservers()

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadAves(forceRefresh = true) }
        viewModel.loadAves()
    }

    private fun setupRecyclerViews() {
        binding.recyclerView.apply {
            adapter = avesAdapter
            layoutManager = LinearLayoutManager(this@AvesActivity)
        }
        
        binding.searchRecyclerView.apply {
            adapter = searchAdapter
            layoutManager = LinearLayoutManager(this@AvesActivity)
        }
    }

    private fun setupSearch() {
        binding.searchView.setupWithSearchBar(binding.searchBar)
        
        binding.searchView.editText.setOnEditorActionListener { v, _, _ ->
            binding.searchBar.setText(binding.searchView.text)
            binding.searchView.hide()
            viewModel.updateSearch(v.text.toString())
            false
        }

        binding.searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateSearch(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun setupObservers() {
        viewModel.avesState.observe(this) { state -> renderState(state) }
        viewModel.filteredAves.observe(this) { aves -> 
            searchAdapter.submitList(aves)
        }
    }

    private fun renderState(state: UiState<List<Ave>>) {
        when (state) {
            UiState.Loading -> setLoading(true)
            is UiState.Success -> {
                setLoading(false)
                avesAdapter.submitList(state.data)
            }
            is UiState.Empty -> {
                setLoading(false)
                avesAdapter.submitList(emptyList())
            }
            is UiState.Error -> {
                setLoading(false)
                Toast.makeText(
                    this,
                    "Error al cargar: ${state.throwable?.localizedMessage ?: "desconocido"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setLoading(show: Boolean) {
        binding.swipeRefresh.isRefreshing = show
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private inner class AvesAdapter : androidx.recyclerview.widget.ListAdapter<Ave, AveViewHolder>(AveDiffCallback()) {
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): AveViewHolder {
            val itemBinding = ItemAveBinding.inflate(layoutInflater, parent, false)
            return AveViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: AveViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private inner class AveViewHolder(private val itemBinding: ItemAveBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(item: Ave) {
            itemBinding.titleText.text = item.titulo
            itemBinding.subtitleText.text = item.nombreCientifico
            Glide.with(itemBinding.root)
                .load(item.imageUrl())
                .centerCrop()
                .into(itemBinding.thumbnailImage)
        }
    }

    private class AveDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Ave>() {
        override fun areItemsTheSame(oldItem: Ave, newItem: Ave) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Ave, newItem: Ave) = oldItem == newItem
    }
}

