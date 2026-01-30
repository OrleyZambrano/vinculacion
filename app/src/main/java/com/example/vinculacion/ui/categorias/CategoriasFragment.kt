package com.example.vinculacion.ui.categorias

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vinculacion.R
import com.example.vinculacion.data.model.Ave
import com.example.vinculacion.databinding.FragmentCategoriasBinding
import com.example.vinculacion.databinding.ItemAveBinding
import com.example.vinculacion.ui.common.UiState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File

class CategoriasFragment : Fragment() {

    private var _binding: FragmentCategoriasBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CategoriasViewModel by viewModels()
    private val avesAdapter = SimpleAvesAdapter { ave -> showDetailCard(ave) }
    private var listener: CategoriasInteractions? = null
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var pendingImageUri: Uri? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var currentDetailAve: Ave? = null

    // Lanzadores de permisos y cámara
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        
        if (cameraGranted && locationGranted) {
            openCamera()
        } else {
            showMessage("Se necesitan permisos de cámara y ubicación para esta función")
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingImageUri != null) {
            processImage(pendingImageUri!!)
        }
    }

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
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupSwipeRefresh()
        setupFloatingActionButton()
        setupDetailCard()
        observeViewModel()
        viewModel.loadAves()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            adapter = avesAdapter
            // GridLayoutManager con 2 columnas y tarjetas cuadradas grandes
            val gridLayoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
            layoutManager = gridLayoutManager
            
            // ItemDecoration para tarjetas cuadradas aprovechando mejor el espacio
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    super.getItemOffsets(outRect, view, parent, state)
                    // Calcular ancho disponible reduciendo márgenes mínimos
                    val totalPadding = parent.paddingLeft + parent.paddingRight
                    val spacing = 8 // Espacio mínimo entre tarjetas
                    val width = (parent.width - totalPadding - spacing) / 2
                    view.layoutParams.height = width
                }
            })
            
            // Añadir animación suave para cambios de items
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 200
                changeDuration = 200
                moveDuration = 200
                removeDuration = 200
            }
            // Habilitar caché de vistas para mejor rendimiento
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener { editable ->
            viewModel.updateSearch(editable?.toString().orEmpty())
        }
    }

    private fun setupFilters() {
        // Se llenan dinámicamente desde el ViewModel
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadAves(forceRefresh = true)
        }
    }

    private fun setupFloatingActionButton() {
        // El FAB está en el layout, configurar click para mostrar menú
        binding.recognitionFab.setOnClickListener { view ->
            showFabMenu(view)
        }
    }
    
    private fun showFabMenu(view: View) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(R.menu.fab_menu, popupMenu.menu)
        
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_photo -> {
                    listener?.openRecognition() ?: showMessage("No se pudo abrir reconocimiento")
                    true
                }
                R.id.menu_audio -> {
                    listener?.openRecognition() ?: showMessage("No se pudo abrir reconocimiento")
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }

    private fun setupDetailCard() {
        binding.detailCloseBtn.setOnClickListener {
            hideDetailCard()
        }
        binding.detailBackdrop.setOnClickListener {
            hideDetailCard()
        }
        // Click en la imagen del modal para abrir vista con zoom
        binding.detailImage.setOnClickListener {
            currentDetailAve?.let { ave -> showImageZoomDialog(ave) }
        }
        binding.detailSoundButton.setOnClickListener {
            val isPlaying = mediaPlayer?.isPlaying == true
            if (isPlaying) {
                stopAudio()
            } else {
                val ave = currentDetailAve
                if (ave != null) {
                    playBirdSound(ave)
                } else {
                    showMessage("Audio no disponible")
                }
            }
        }
    }

    private fun checkPermissionsAndTakePhoto() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionsToRequest = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            openCamera()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun openCamera() {
        getCurrentLocation { location ->
            currentLocation = location
            
            try {
                // Crear directorio para archivos de reconocimiento
                val mediaDir = File(requireContext().filesDir, "recognition_media")
                if (!mediaDir.exists()) {
                    mediaDir.mkdirs()
                }
                
                val imageFile = File(mediaDir, "temp_image_${System.currentTimeMillis()}.jpg")
                
                pendingImageUri = FileProvider.getUriForFile(
                    requireContext(),
                    "com.example.vinculacion.fileprovider",
                    imageFile
                )
                
                takePictureLauncher.launch(pendingImageUri)
            } catch (e: Exception) {
                showMessage("Error al abrir cámara")
            }
        }
    }

    private fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    callback(location)
                }
                .addOnFailureListener {
                    showMessage("No se pudo obtener la ubicación")
                    callback(null)
                }
        } else {
            callback(null)
        }
    }

    private fun processImage(imageUri: Uri) {
        val location = currentLocation
        val locationText = if (location != null) {
            "Lat: ${String.format("%.6f", location.latitude)}, Lng: ${String.format("%.6f", location.longitude)}"
        } else {
            "Ubicación no disponible"
        }

        showMessage("Imagen capturada. $locationText")
        
        // Aquí se integraría con el sistema de reconocimiento
        // Por ahora solo mostramos la información
        showRecognitionInfo(imageUri, location)
    }

    private fun showRecognitionInfo(imageUri: Uri, location: Location?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_recognition_info, null)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Referencias a las vistas del diálogo
        val dialogImage = dialogView.findViewById<ImageView>(R.id.dialogImage)
        val dialogLocationText = dialogView.findViewById<TextView>(R.id.dialogLocationText)
        val dialogDateTimeText = dialogView.findViewById<TextView>(R.id.dialogDateTimeText)
        val dialogStatusText = dialogView.findViewById<TextView>(R.id.dialogStatusText)
        val dialogCloseBtn = dialogView.findViewById<View>(R.id.dialogCloseBtn)
        val dialogRetryBtn = dialogView.findViewById<View>(R.id.dialogRetryBtn)
        val dialogCloseActionBtn = dialogView.findViewById<View>(R.id.dialogCloseActionBtn)
        
        // Cargar imagen capturada
        Glide.with(this)
            .load(imageUri)
            .placeholder(R.drawable.image_placeholder)
            .error(R.drawable.image_placeholder)
            .into(dialogImage)
        
        // Mostrar información de ubicación
        val locationText = if (location != null) {
            "Latitud: ${String.format("%.6f", location.latitude)}\n" +
            "Longitud: ${String.format("%.6f", location.longitude)}\n" +
            "Precisión: ${String.format("%.1f", location.accuracy)}m"
        } else {
            "Ubicación no disponible\n" +
            "Verifique los permisos de ubicación"
        }
        dialogLocationText.text = locationText
        
        // Mostrar fecha y hora actual
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm:ss", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())
        dialogDateTimeText.text = currentDateTime
        
        // Estado del reconocimiento
        dialogStatusText.text = "Imagen capturada exitosamente\n" +
            "Tamaño del archivo: ${getImageFileSize(imageUri)}\n" +
            "Reconocimiento: Listo para procesar"
        
        // Configurar botones
        dialogCloseBtn.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogRetryBtn.setOnClickListener {
            dialog.dismiss()
            // Tomar otra foto
            checkPermissionsAndTakePhoto()
        }
        
        dialogCloseActionBtn.setOnClickListener {
            dialog.dismiss()
            showMessage("Información guardada")
        }
        
        dialog.show()
    }
    
    private fun getImageFileSize(uri: Uri): String {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val sizeInBytes = inputStream?.available() ?: 0
            inputStream?.close()
            
            when {
                sizeInBytes > 1024 * 1024 -> String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0))
                sizeInBytes > 1024 -> String.format("%.1f KB", sizeInBytes / 1024.0)
                else -> "$sizeInBytes bytes"
            }
        } catch (e: Exception) {
            "Tamaño desconocido"
        }
    }

    private fun playBirdSound(ave: Ave) {
        try {
            // Detener reproducción anterior si existe
            mediaPlayer?.release()
            mediaPlayer = null
            
            if (ave.sonido.isNotEmpty()) {
                val soundUrl = ave.soundUrl()
                showMessage("🔊 ${ave.nombreComun}")
                
                // Crear nuevo MediaPlayer
                mediaPlayer = android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    
                    try {
                        setDataSource(soundUrl)
                        
                        setOnPreparedListener { mp ->
                            try {
                                mp.start()
                                showMessage("♪ Reproduciendo...")
                                updateSoundButtonState(true)
                            } catch (e: Exception) {
                                showMessage("❌ Error al reproducir")
                            }
                        }
                        
                        setOnErrorListener { _, what, extra ->
                            showMessage("📡 Audio no disponible en servidor")
                            mediaPlayer?.release()
                            mediaPlayer = null
                            updateSoundButtonState(false)
                            true
                        }
                        
                        setOnCompletionListener { mp ->
                            mp.release()
                            mediaPlayer = null
                            showMessage("✅ Audio terminado")
                            updateSoundButtonState(false)
                        }
                        
                        // Timeout de 5 segundos
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
                                showMessage("⏱️ Archivo no encontrado en servidor")
                                mediaPlayer?.release()
                                mediaPlayer = null
                                updateSoundButtonState(false)
                            }
                        }, 5000)
                        
                        prepareAsync()
                        
                    } catch (e: Exception) {
                        showMessage("❌ Error de conexión")
                        release()
                        mediaPlayer = null
                        updateSoundButtonState(false)
                    }
                }
            } else {
                showMessage("🔇 Sin archivo de audio")
            }
        } catch (e: Exception) {
            showMessage("❌ Error: ${e.localizedMessage}")
            mediaPlayer?.release()
            mediaPlayer = null
            updateSoundButtonState(false)
        }
    }
    
    private fun tryAlternativeAudio(ave: Ave) {
        // Esta función ya no es necesaria - removida
    }
    
    private fun showAudioRecognitionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("🎤 Reconocimiento por Audio")
            .setMessage("Esta función permite identificar aves por su canto.\n\n⚠️ Próximamente disponible")
            .setPositiveButton("Entendido") { _, _ -> }
            .setNegativeButton("Tomar Foto") { _, _ -> 
                checkPermissionsAndTakePhoto()
            }
            .show()
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun showImageZoomDialog(ave: Ave) {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogView = layoutInflater.inflate(R.layout.dialog_image_zoom, null)
        
        val photoView = dialogView.findViewById<com.github.chrisbanes.photoview.PhotoView>(R.id.zoomImageView)
        val closeButton = dialogView.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.closeButton)
        val titleView = dialogView.findViewById<TextView>(R.id.imageTitle)
        
        titleView.text = ave.nombreComun
        
        // Cargar imagen con Glide
        Glide.with(this)
            .load(ave.imageUrl())
            .placeholder(R.drawable.image_placeholder)
            .error(R.drawable.image_placeholder)
            .into(photoView)
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Cerrar al tocar el fondo
        dialogView.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun showDetailCard(ave: Ave) {
        stopAudio()
        currentDetailAve = ave
        // Llenar la tarjeta con datos del ave
        binding.detailTitle.text = ave.nombreComun
        binding.detailScientific.text = ave.nombreCientifico
        binding.detailFamily.text = ave.familia
        binding.detailEnglish.text = ave.nombreIngles
        binding.detailDescription.text = ave.descripcion
        
        // Cargar imagen
        Glide.with(this)
            .load(ave.imageUrl())
            .placeholder(R.drawable.image_placeholder)
            .error(R.drawable.image_placeholder)
            .into(binding.detailImage)
            
        // Mostrar la tarjeta
        binding.detailCard.visibility = View.VISIBLE
        binding.detailBackdrop.visibility = View.VISIBLE
        
        updateSoundButtonState(false)
    }
    
    private fun hideDetailCard() {
        stopAudio()
        binding.detailCard.visibility = View.GONE
        binding.detailBackdrop.visibility = View.GONE
        currentDetailAve = null
    }

    private fun stopAudio() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
            // Ignorar estados inválidos
        } finally {
            mediaPlayer?.release()
            mediaPlayer = null
        }
        updateSoundButtonState(false)
    }

    private fun updateSoundButtonState(isPlaying: Boolean) {
        if (!isAdded || _binding == null) return
        if (isPlaying) {
            binding.detailSoundButton.text = "Detener"
        } else {
            binding.detailSoundButton.text = getString(R.string.listen_audio)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.avesState.collect { state ->
                        when (state) {
                            is UiState.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.recyclerView.visibility = View.GONE
                                binding.emptyText.visibility = View.GONE
                            }
                            is UiState.Success -> {
                                binding.progressBar.visibility = View.GONE
                                binding.recyclerView.visibility = View.VISIBLE
                                binding.emptyText.visibility = View.GONE
                                binding.swipeRefresh.isRefreshing = false
                            }
                            is UiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.recyclerView.visibility = View.GONE
                                binding.emptyText.visibility = View.VISIBLE
                                binding.emptyText.text = state.message ?: "Error al cargar aves"
                                binding.swipeRefresh.isRefreshing = false
                            }
                            else -> {}
                        }
                    }
                }
                
                launch {
                    viewModel.filteredAves.collect { aves ->
                        // Usar submitList con callback para animación suave
                        avesAdapter.submitList(aves) {
                            // Scroll suave al inicio solo si hay un cambio significativo de filtro
                            if (aves.isNotEmpty() && viewModel.avesState.value is UiState.Success) {
                                binding.recyclerView.post {
                                    binding.recyclerView.smoothScrollToPosition(0)
                                }
                            }
                        }
                        
                        if (aves.isEmpty() && viewModel.avesState.value is UiState.Success) {
                            binding.recyclerView.visibility = View.GONE
                            binding.emptyText.visibility = View.VISIBLE
                            binding.emptyText.text = "No se encontraron aves"
                        }
                    }
                }

                launch {
                    viewModel.availableFamilies.collect { families ->
                        renderFilters(families)
                    }
                }

                launch {
                    viewModel.activeFilter.collect { active ->
                        updateActiveFilter(active)
                    }
                }
            }
        }
    }

    private fun renderFilters(families: List<String>) {
        val chipGroup = binding.filtersChipGroup
        chipGroup.removeAllViews()

        // Chip "Todos"
        val allChip = Chip(requireContext()).apply {
            text = "Todos"
            isCheckable = true
            isChecked = viewModel.activeFilter.value.isEmpty()
            setOnClickListener {
                viewModel.setFilter("")
            }
        }
        chipGroup.addView(allChip)

        families.forEach { familia ->
            val chip = Chip(requireContext()).apply {
                text = familia
                isCheckable = true
                isChecked = viewModel.activeFilter.value == familia
                setOnClickListener {
                    viewModel.setFilter(familia)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun updateActiveFilter(active: String) {
        val chipGroup = binding.filtersChipGroup
        for (i in 0 until chipGroup.childCount) {
            val view = chipGroup.getChildAt(i)
            if (view is Chip) {
                view.isChecked = view.text.toString() == active || (active.isEmpty() && view.text.toString() == "Todos")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        mediaPlayer = null
        _binding = null
    }

    override fun onDetach() {
        listener = null
        super.onDetach()
    }

    override fun onPause() {
        super.onPause()
        stopAudio()
    }

    companion object {
        fun newInstance() = CategoriasFragment()
    }

    interface CategoriasInteractions {
        fun openTours()
        fun openMap()
        fun openRecognition()
        fun openProfile()
    }

    private class SimpleAvesAdapter(
        private val onItemClick: (Ave) -> Unit
    ) : ListAdapter<Ave, SimpleAvesAdapter.AveViewHolder>(AveDiffCallback) {

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
            fun bind(ave: Ave) {
                binding.titleText.text = ave.nombreComun
                binding.familyText.text = ave.familia ?: "Familia desconocida"
                
                Glide.with(binding.root.context)
                    .load(ave.imageUrl())
                    .placeholder(R.drawable.image_placeholder)
                    .error(R.drawable.image_placeholder)
                    .centerCrop()
                    .into(binding.thumbnailImage)
                    
                // Agregar click listener a toda la tarjeta
                binding.root.setOnClickListener {
                    onItemClick(ave)
                }
            }
        }

        object AveDiffCallback : DiffUtil.ItemCallback<Ave>() {
            override fun areItemsTheSame(oldItem: Ave, newItem: Ave): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Ave, newItem: Ave): Boolean {
                return oldItem == newItem
            }
        }
    }
}

