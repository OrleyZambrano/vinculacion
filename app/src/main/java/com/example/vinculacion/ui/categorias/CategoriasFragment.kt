package com.example.vinculacion.ui.categorias

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vinculacion.R
import com.example.vinculacion.data.model.Ave
import com.example.vinculacion.databinding.FragmentCategoriasBinding
import com.example.vinculacion.databinding.ItemAveBinding
import com.example.vinculacion.ui.aves.AvesViewModel
import com.example.vinculacion.ui.common.ListContentStateHandler
import com.example.vinculacion.ui.common.UiState

class CategoriasFragment : Fragment() {

    private var _binding: FragmentCategoriasBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AvesViewModel by viewModels()
    private val avesAdapter = SimpleAvesAdapter { showDetailCard(it) }
    private var selectedAve: Ave? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioUrl: String? = null
    private lateinit var listStateHandler: ListContentStateHandler
    private var listener: CategoriasInteractions? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? CategoriasInteractions
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = avesAdapter
        }
        listStateHandler = ListContentStateHandler(
            recyclerView = binding.recyclerView,
            progressView = binding.progressBar,
            emptyView = binding.emptyText
        )
        setupSearchAndFilters()
        setupRecognitionFab()
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadAves(forceRefresh = true) }
        binding.detailCloseBtn.setOnClickListener {
            hideDetailCard()
            stopAudio(resetButton = false)
        }
        binding.detailBackdrop.setOnClickListener {
            hideDetailCard()
            stopAudio(resetButton = false)
        }
        viewModel.filteredAves.observe(viewLifecycleOwner) { list ->
            if (selectedAve != null && list.none { it.id == selectedAve?.id }) {
                hideDetailCard()
            }
            avesAdapter.submitList(list)
            when (viewModel.avesState.value) {
                is UiState.Success, is UiState.Empty -> listStateHandler.showContent(
                    list,
                    hasActiveModifiers(),
                    getString(R.string.empty_aves),
                    getString(R.string.empty_filtered_aves)
                )
                else -> Unit
            }
        }
        viewModel.avesState.observe(viewLifecycleOwner) { state ->
            when (state) {
                UiState.Loading -> {
                    if (!binding.swipeRefresh.isRefreshing) {
                        listStateHandler.showLoading()
                    }
                }
                is UiState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    populateFilterChips()
                    val currentList = viewModel.filteredAves.value ?: state.data
                    listStateHandler.showContent(
                        currentList,
                        hasActiveModifiers(),
                        getString(R.string.empty_aves),
                        getString(R.string.empty_filtered_aves)
                    )
                    if (selectedAve != null) {
                        lastSelectedFromState(state.data)
                    }
                }
                is UiState.Empty -> {
                    binding.swipeRefresh.isRefreshing = false
                    populateFilterChips()
                    listStateHandler.showEmpty(state.message ?: getString(R.string.empty_aves))
                    hideDetailCard()
                }
                is UiState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    listStateHandler.showError(state.message ?: state.throwable?.localizedMessage ?: getString(R.string.error_loading_aves))
                    hideDetailCard()
                }
            }
        }
        viewModel.loadAves()
    }

    private fun setupSearchAndFilters() {
        binding.searchEditText.addTextChangedListener { editable ->
            viewModel.updateSearch(editable?.toString().orEmpty())
        }
        binding.filtersChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val chipId = checkedIds.firstOrNull()
            val chip = chipId?.let { group.findViewById<com.google.android.material.chip.Chip>(it) }
            viewModel.setFilter(chip?.tag as? String)
        }
    }

    private fun populateFilterChips() {
        val chipGroup = binding.filtersChipGroup
        if (chipGroup.childCount > 0) return
        val families = viewModel.availableFamilies()
        val context = chipGroup.context

        val allChip = com.google.android.material.chip.Chip(context).apply {
            text = getString(R.string.filter_all)
            isCheckable = true
            isChecked = true
            tag = null
        }
        chipGroup.addView(allChip)

        families.forEach { family ->
            val chip = com.google.android.material.chip.Chip(context).apply {
                text = family
                tag = family
                isCheckable = true
            }
            chipGroup.addView(chip)
        }
    }

    private fun showDetailCard(ave: Ave) {
        selectedAve = ave
        binding.detailBackdrop.visibility = View.VISIBLE
        binding.detailTitle.text = ave.titulo
        binding.detailScientific.text = ave.nombreCientifico
        binding.detailFamily.text = getString(R.string.detail_family_format, ave.familia)
        binding.detailDescription.text = ave.descripcion
        binding.detailBadge.text = ave.nombreComun
        binding.detailBadge.visibility = if (ave.nombreComun.isNullOrBlank()) View.GONE else View.VISIBLE
        val hasAudio = ave.sonido.isNotBlank()
        binding.detailSoundButton.visibility = if (hasAudio) View.VISIBLE else View.GONE
        binding.detailSoundButton.isEnabled = hasAudio
        binding.detailSoundButton.alpha = if (hasAudio) 1f else 0.5f
        binding.detailSoundButton.text = if (hasAudio && currentAudioUrl == ave.sonido && mediaPlayer?.isPlaying == true) {
            getString(R.string.pause_audio)
        } else {
            getString(R.string.play_audio)
        }
        binding.detailSoundButton.setOnClickListener {
            if (currentAudioUrl == ave.sonido && mediaPlayer?.isPlaying == true) {
                stopAudio()
            } else {
                playAudio(ave.sonido)
            }
        }
        Glide.with(binding.detailImage)
            .load(ave.imageUrl())
            .centerCrop()
            .placeholder(R.drawable.image_placeholder)
            .into(binding.detailImage)
        binding.detailCard.visibility = View.VISIBLE
    }

    private fun playAudio(url: String) {
        if (url.isBlank()) return
        stopAudio()
        binding.detailSoundButton.text = getString(R.string.audio_loading)
        binding.detailSoundButton.isEnabled = false
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(url)
            setOnPreparedListener {
                binding.detailSoundButton.isEnabled = true
                binding.detailSoundButton.text = getString(R.string.pause_audio)
                it.start()
            }
            setOnCompletionListener {
                binding.detailSoundButton.text = getString(R.string.play_audio)
            }
            setOnErrorListener { _, _, _ ->
                binding.detailSoundButton.isEnabled = true
                binding.detailSoundButton.text = getString(R.string.play_audio)
                Toast.makeText(requireContext(), R.string.audio_error, Toast.LENGTH_SHORT).show()
                stopAudio(resetButton = false)
                true
            }
            prepareAsync()
        }
        currentAudioUrl = url
    }

    private fun stopAudio(resetButton: Boolean = true) {
        mediaPlayer?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        mediaPlayer = null
        currentAudioUrl = null
        if (resetButton) {
            binding.detailSoundButton.text = getString(R.string.play_audio)
        }
    }

    private fun hideDetailCard() {
        selectedAve = null
        binding.detailCard.visibility = View.GONE
        binding.detailBackdrop.visibility = View.GONE
    }

    private fun lastSelectedFromState(list: List<Ave>) {
        val match = selectedAve?.id?.let { id -> list.find { it.id == id } }
        if (match != null) {
            showDetailCard(match)
        } else {
            hideDetailCard()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAudio()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun hasActiveModifiers(): Boolean {
        val queryActive = !binding.searchEditText.text.isNullOrBlank()
        val filterActive = !viewModel.activeFilter.value.isNullOrBlank()
        return queryActive || filterActive
    }

    private fun setupRecognitionFab() {
        binding.recognitionFab.setOnClickListener {
            showRecognitionOptionsBottomSheet()
        }
    }

    private fun showRecognitionOptionsBottomSheet() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_recognition_options, null)
        bottomSheet.setContentView(bottomSheetView)

        bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.optionCapturePhoto).setOnClickListener {
            bottomSheet.dismiss()
            listener?.openRecognition()
        }

        bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.optionRecordAudio).setOnClickListener {
            bottomSheet.dismiss()
            listener?.openRecognition()
        }

        bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.optionChooseGallery).setOnClickListener {
            bottomSheet.dismiss()
            listener?.openRecognition()
        }

        bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.optionViewMap).setOnClickListener {
            bottomSheet.dismiss()
            listener?.openMap()
        }

        bottomSheet.show()
    }

    interface CategoriasInteractions {
        fun openRecognition()
        fun openMap()
    }
}

private class SimpleAvesAdapter(
    private val onItemClick: (Ave) -> Unit
) : ListAdapter<Ave, SimpleAvesAdapter.AveViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AveViewHolder {
        val binding = ItemAveBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AveViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: AveViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AveViewHolder(
        private val binding: ItemAveBinding,
        private val onItemClick: (Ave) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Ave) {
            binding.titleText.text = item.titulo
            binding.subtitleText.text = item.nombreCientifico
            Glide.with(binding.root)
                .load(item.imageUrl())
                .centerCrop()
                .into(binding.thumbnailImage)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Ave>() {
        override fun areItemsTheSame(oldItem: Ave, newItem: Ave) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Ave, newItem: Ave) = oldItem == newItem
    }
}

