package com.example.vinculacion.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.vinculacion.MainActivity
import com.example.vinculacion.R
import com.example.vinculacion.databinding.FragmentHomeBinding
import com.example.vinculacion.ui.common.UiState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private val prefs: SharedPreferences by lazy {
        requireContext().getSharedPreferences("location_prefs", Context.MODE_PRIVATE)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        // Marcar que ya se solicitó el permiso
        prefs.edit().putBoolean(PREF_PERMISSION_REQUESTED, true).apply()
        
        if (fineLocationGranted || coarseLocationGranted) {
            // Permiso concedido, cargar clima
            viewModel.loadWeather()
        } else {
            // Permiso denegado, mostrar estado apropiado
            showPermissionDeniedState()
        }
    }

    private val carouselAdapter = HomeCarouselAdapter {
        listener?.openCategories()
    }
    private val ecoTipsAdapter = EcoTipsAdapter()
    private val quickActionsAdapter = HomeQuickActionsAdapter { action ->
        listener?.let { interactions ->
            when (action) {
                HomeAction.CATEGORIES -> interactions.openCategories()
                HomeAction.TOURS -> interactions.openTours()
                HomeAction.MAP -> interactions.openMap()
                HomeAction.RECOGNITION -> interactions.openRecognition()
                HomeAction.PROFILE -> interactions.openProfile()
            }
        }
    }

    private var listener: HomeInteractions? = null
    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null
    private var ecoTipsAutoScrollRunnable: Runnable? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? HomeInteractions
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCarousel()
        setupEcoTipsCarousel()
        setupQuickActions()
        setupRecognitionHero()
        setupSwipeRefresh()
        setupWeatherCard()
        setupScrollBehavior()
        collectHomeState()
        collectWeatherState()
    }

    private fun setupCarousel() {
        binding.topBirdsPager.adapter = carouselAdapter
        binding.topBirdsPager.offscreenPageLimit = 1
        
        // Iniciar en el medio de la lista infinita
        binding.topBirdsPager.post {
            if (carouselAdapter.itemCount > 0) {
                binding.topBirdsPager.setCurrentItem(Int.MAX_VALUE / 2, false)
            }
        }
        
        // Pausar el auto-scroll cuando el usuario está interactuando
        binding.topBirdsPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING) {
                    stopAutoScroll()
                } else if (state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE) {
                    setupAutoScroll()
                }
            }
        })
        
        setupAutoScroll()
    }

    private fun setupAutoScroll() {
        // Detener cualquier auto-scroll anterior
        stopAutoScroll()
        
        autoScrollRunnable = object : Runnable {
            override fun run() {
                if (_binding == null) return
                val itemCount = carouselAdapter.itemCount
                if (itemCount > 0) {
                    val currentItem = binding.topBirdsPager.currentItem
                    binding.topBirdsPager.setCurrentItem(currentItem + 1, true)
                }
                autoScrollHandler.postDelayed(this, 5000) // Cambiar cada 5 segundos
            }
        }
        autoScrollRunnable?.let { autoScrollHandler.postDelayed(it, 5000) }
    }

    private fun stopAutoScroll() {
        autoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
    }

    private fun setupEcoTipsCarousel() {
        binding.ecoTipsPager.adapter = ecoTipsAdapter
        binding.ecoTipsPager.offscreenPageLimit = 1
        
        // Pausar el auto-scroll cuando el usuario está interactuando
        binding.ecoTipsPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING) {
                    stopEcoTipsAutoScroll()
                } else if (state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE) {
                    setupEcoTipsAutoScroll()
                }
            }
        })
        
        setupEcoTipsAutoScroll()
    }

    private fun setupEcoTipsAutoScroll() {
        // Detener cualquier auto-scroll anterior
        stopEcoTipsAutoScroll()
        
        ecoTipsAutoScrollRunnable = object : Runnable {
            override fun run() {
                if (_binding == null) return
                val itemCount = ecoTipsAdapter.itemCount
                if (itemCount > 1) {
                    val currentItem = binding.ecoTipsPager.currentItem
                    val nextItem = (currentItem + 1) % itemCount
                    binding.ecoTipsPager.setCurrentItem(nextItem, true)
                }
                autoScrollHandler.postDelayed(this, 6000) // Cambiar cada 6 segundos
            }
        }
        ecoTipsAutoScrollRunnable?.let { autoScrollHandler.postDelayed(it, 6000) }
    }

    private fun stopEcoTipsAutoScroll() {
        ecoTipsAutoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
    }

    private fun setupQuickActions() {
        binding.quickActionsList.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = quickActionsAdapter
        }
    }



    private fun setupRecognitionHero() {
        binding.homeRecognitionCard.setOnClickListener {
            listener?.openRecognition()
        }
        binding.recognitionHeroPhotoButton.setOnClickListener {
            listener?.openRecognition()
        }
        binding.recognitionHeroAudioButton.setOnClickListener {
            listener?.openRecognition()
        }
    }

    private fun setupSwipeRefresh() {
        binding.homeSwipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    private fun setupWeatherCard() {
        binding.weatherRefreshButton?.setOnClickListener {
            checkLocationPermissionAndLoadWeather()
        }
        
        // Botón de acción cuando hay error
        binding.weatherErrorAction?.setOnClickListener {
            handleWeatherErrorAction()
        }
        
        // Cargar clima solo si no hay datos previos (primera vez o después de error)
        if (viewModel.weatherState.value !is UiState.Success) {
            checkLocationPermissionAndLoadWeather()
        }
    }

    private fun setupScrollBehavior() {
        binding.homeScrollView?.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val bannerHeight = resources.getDimensionPixelSize(R.dimen.banner_height)
            
            // Calcular el desplazamiento del banner basado en el scroll
            val translation = -scrollY.toFloat().coerceAtMost(bannerHeight.toFloat())
            
            // Mover el banner hacia arriba (solo cuando estamos en Home)
            (activity as? MainActivity)?.animateBannerTranslation(translation)
        }
    }

    private fun handleWeatherErrorAction() {
        when {
            hasLocationPermission() -> {
                // Si ya tiene permiso, solo recargar
                viewModel.loadWeather()
            }
            shouldShowPermissionRationale() -> {
                // Mostrar diálogo educativo y solicitar de nuevo
                showLocationPermissionEducationDialog()
            }
            wasPermissionRequested() -> {
                // Ya se solicitó y el usuario marcó "No volver a preguntar" → ir a Settings
                openAppSettings()
            }
            else -> {
                // Primera vez, solicitar directamente
                requestLocationPermission()
            }
        }
    }

    private fun collectWeatherState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.weatherState.collect { state ->
                    when (state) {
                        UiState.Loading -> {
                            binding.weatherProgressBar?.isVisible = true
                            binding.weatherContent?.isVisible = false
                            binding.weatherError?.isVisible = false
                        }
                        is UiState.Success -> {
                            binding.weatherProgressBar?.isVisible = false
                            binding.weatherContent?.isVisible = true
                            binding.weatherError?.isVisible = false
                            renderWeather(state.data)
                        }
                        is UiState.Error -> {
                            binding.weatherProgressBar?.isVisible = false
                            binding.weatherContent?.isVisible = false
                            binding.weatherError?.isVisible = true
                            binding.weatherErrorText?.text = state.throwable?.localizedMessage
                                ?: getString(R.string.error_loading_weather)
                        }
                        is UiState.Empty -> {
                            binding.weatherProgressBar?.isVisible = false
                            binding.weatherContent?.isVisible = false
                            binding.weatherError?.isVisible = true
                        }
                    }
                }
            }
        }
    }

    private fun renderWeather(weather: com.example.vinculacion.data.model.Weather) {
        binding.apply {
            // Mostrar temperatura y descripción por separado
            val ageMinutes = (System.currentTimeMillis() - weather.timestamp) / (60 * 1000)
            val description = if (ageMinutes > 15) {
                "${weather.description.replaceFirstChar { it.uppercase() }} (Guardado)"
            } else {
                weather.description.replaceFirstChar { it.uppercase() }
            }
            
            weatherTemperature?.text = getString(R.string.weather_temperature, weather.temperatureCelsius)
            weatherDescription?.text = description
            
            weatherBirdActivity?.text = weather.getBirdActivityLevel()
            
            // Load weather icon
            weatherIcon?.let { imageView ->
                Glide.with(requireContext())
                    .load(weather.getIconUrl())
                    .placeholder(R.drawable.ic_cloud)
                    .error(R.drawable.ic_cloud)
                    .into(imageView)
            }
        }
    }

    // ====== Permission Management ======

    private fun checkLocationPermissionAndLoadWeather() {
        when {
            hasLocationPermission() -> {
                // Permiso ya concedido, cargar clima
                viewModel.loadWeather()
            }
            shouldShowPermissionRationale() -> {
                // Mostrar diálogo educativo
                showLocationPermissionEducationDialog()
            }
            else -> {
                // Primera vez, solicitar directamente
                requestLocationPermission()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun shouldShowPermissionRationale(): Boolean {
        return shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun wasPermissionRequested(): Boolean {
        return prefs.getBoolean(PREF_PERMISSION_REQUESTED, false)
    }

    private fun showLocationPermissionEducationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.location_permission_title)
            .setMessage(R.string.location_permission_message)
            .setIcon(R.drawable.ic_location)
            .setPositiveButton(R.string.location_permission_allow) { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton(R.string.location_permission_deny) { dialog, _ ->
                dialog.dismiss()
                showPermissionDeniedState()
            }
            .setCancelable(true)
            .setOnCancelListener {
                showPermissionDeniedState()
            }
            .show()
    }

    private fun requestLocationPermission() {
        // Marcar que estamos solicitando el permiso
        prefs.edit().putBoolean(PREF_PERMISSION_REQUESTED, true).apply()
        
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showPermissionDeniedState() {
        binding.apply {
            weatherProgressBar?.isVisible = false
            weatherContent?.isVisible = false
            weatherError?.isVisible = true
            
            when {
                shouldShowPermissionRationale() -> {
                    // Usuario denegó pero puede volver a preguntar
                    weatherErrorText?.text = getString(R.string.location_permission_denied_message)
                    weatherErrorAction?.isVisible = true
                    weatherErrorAction?.text = getString(R.string.location_permission_retry)
                }
                wasPermissionRequested() && !hasLocationPermission() -> {
                    // Usuario seleccionó "No volver a preguntar" (ya se solicitó y no tiene permiso)
                    weatherErrorText?.text = getString(R.string.location_permission_denied_permanently)
                    weatherErrorAction?.isVisible = true
                    weatherErrorAction?.text = getString(R.string.location_permission_settings)
                }
                else -> {
                    // Estado inicial (nunca se solicitó)
                    weatherErrorText?.text = getString(R.string.location_permission_initial_message)
                    weatherErrorAction?.isVisible = true
                    weatherErrorAction?.text = getString(R.string.location_permission_allow)
                }
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }

    private fun collectHomeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.homeState.collect { state ->
                    when (state) {
                        UiState.Loading -> binding.homeSwipeRefresh.isRefreshing = true
                        is UiState.Success -> renderHome(state.data)
                        is UiState.Error -> {
                            binding.homeSwipeRefresh.isRefreshing = false
                            binding.emptyCarouselText.isVisible = true
                            binding.emptyCarouselText.text = state.throwable?.localizedMessage
                                ?: getString(R.string.error_loading_aves)
                        }
                        is UiState.Empty -> {
                            binding.homeSwipeRefresh.isRefreshing = false
                            renderHome(
                                HomeUiData(emptyList(), emptyList(), viewModel.defaultQuickActions())
                            )
                        }
                    }
                }
            }
        }
    }

    private fun renderHome(data: HomeUiData) {
        binding.homeSwipeRefresh.isRefreshing = false
        carouselAdapter.submitList(data.topBirds)
        binding.emptyCarouselText.isVisible = data.topBirds.isEmpty()
        quickActionsAdapter.submitList(data.quickActions)
    }

    override fun onDestroyView() {
        stopAutoScroll()
        stopEcoTipsAutoScroll()
        binding.topBirdsPager.adapter = null
        binding.ecoTipsPager.adapter = null
        binding.quickActionsList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
        stopEcoTipsAutoScroll()
    }

    override fun onResume() {
        super.onResume()
        setupAutoScroll()
        setupEcoTipsAutoScroll()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    interface HomeInteractions {
        fun openCategories()
        fun openTours()
        fun openMap()
        fun openRecognition()
        fun openProfile()
    }

    companion object {
        private const val PREF_PERMISSION_REQUESTED = "location_permission_requested"
        
        fun newInstance() = HomeFragment()
    }
}
