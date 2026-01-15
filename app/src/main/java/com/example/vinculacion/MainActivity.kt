package com.example.vinculacion

import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.vinculacion.data.model.UserRole
import com.example.vinculacion.data.repository.AuthRepository
import com.example.vinculacion.ui.categorias.CategoriasFragment
import com.example.vinculacion.ui.guide.GuideDashboardFragment
import com.example.vinculacion.ui.home.HomeFragment
import com.example.vinculacion.ui.map.MapsFragment
import com.example.vinculacion.ui.profile.ProfileFragment
import com.example.vinculacion.ui.recognition.RecognitionFragment
import kotlinx.coroutines.launch

/**
 * Actividad principal de la aplicación Vinculación
 * Contiene la navegación personalizada inferior y el contenido principal
 */
class MainActivity : AppCompatActivity(), HomeFragment.HomeInteractions, CategoriasFragment.CategoriasInteractions {

    private lateinit var contentContainer: FragmentContainerView
    private var currentDestination: Destination = Destination.HOME
    
    private val authRepository by lazy { AuthRepository(this) }
    private var isGuide = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar para respetar las barras del sistema
        window.statusBarColor = getColor(R.color.primary_color)
        window.navigationBarColor = getColor(R.color.white)

        setContentView(R.layout.activity_main)

        contentContainer = findViewById(R.id.main_content_container)
        setupBottomNavigation()
        observeUserRole()

        // Mostrar contenido inicial del home
        navigateTo(Destination.HOME)
    }
    
    private fun observeUserRole() {
        lifecycleScope.launch {
            authRepository.authState.collect { authState ->
                val wasGuide = isGuide
                isGuide = authState.profile.role == UserRole.GUIA
                
                // Si cambió el rol, actualizar la vista actual
                if (wasGuide != isGuide) {
                    updateCurrentView()
                }
            }
        }
    }
    
    private fun updateCurrentView() {
        // Re-navegar al destino actual para actualizar el fragmento
        val current = currentDestination
        currentDestination = Destination.HOME  // Reset para forzar navegación
        navigateTo(current)
    }

    /**
     * Configura la navegación inferior personalizada con 5 botones
     */
    private fun setupBottomNavigation() {
        val navHome = findViewById<LinearLayout>(R.id.nav_home_btn)
        val navCategories = findViewById<LinearLayout>(R.id.nav_categories_btn)
        val navTours = findViewById<LinearLayout>(R.id.nav_tours_btn)
        val navMap = findViewById<LinearLayout>(R.id.nav_map_btn)
        val navProfile = findViewById<LinearLayout>(R.id.nav_profile_btn)

        navHome.setOnClickListener {
            navigateTo(Destination.HOME)
        }

        navCategories.setOnClickListener {
            navigateTo(Destination.CATEGORIES)
        }

        navTours.setOnClickListener {
            navigateTo(Destination.TOURS)
        }

        navMap.setOnClickListener {
            navigateTo(Destination.MAP)
        }

        navProfile.setOnClickListener {
            navigateTo(Destination.PROFILE)
        }
    }

    private fun navigateTo(destination: Destination) {
        if (destination == currentDestination && supportFragmentManager.findFragmentById(R.id.main_content_container) != null) {
            return
        }
        currentDestination = destination
        val fragment = when (destination) {
            Destination.HOME -> HomeFragment.newInstance()
            Destination.CATEGORIES -> CategoriasFragment()
            Destination.TOURS -> {
                if (isGuide) {
                    GuideDashboardFragment.newInstance()
                } else {
                    com.example.vinculacion.ui.tours.ToursFragment.newInstance()
                }
            }
            Destination.MAP -> MapsFragment.newInstance()
            Destination.RECOGNITION -> RecognitionFragment.newInstance()
            Destination.PROFILE -> ProfileFragment.newInstance()
        }
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.main_content_container, fragment)
        }
    }

    override fun openCategories() {
        navigateTo(Destination.CATEGORIES)
    }

    override fun openTours() {
        navigateTo(Destination.TOURS)
    }

    override fun openMap() {
        navigateTo(Destination.MAP)
    }

    override fun openRecognition() {
        navigateTo(Destination.RECOGNITION)
    }

    override fun openProfile() {
        navigateTo(Destination.PROFILE)
    }

    private enum class Destination { HOME, CATEGORIES, TOURS, MAP, PROFILE, RECOGNITION }
}
