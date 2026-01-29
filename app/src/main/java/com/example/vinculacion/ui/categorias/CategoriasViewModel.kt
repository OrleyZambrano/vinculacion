package com.example.vinculacion.ui.categorias

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinculacion.data.model.Ave
import com.example.vinculacion.data.repository.AvesRepository
import com.example.vinculacion.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para el fragmento de categorías/aves
 */
class CategoriasViewModel(application: Application) : AndroidViewModel(application) {
    
    private val avesRepository = AvesRepository(application.applicationContext)
    
    private val _avesState = MutableStateFlow<UiState<List<Ave>>>(UiState.Loading)
    val avesState: StateFlow<UiState<List<Ave>>> = _avesState.asStateFlow()
    
    private val _filteredAves = MutableStateFlow<List<Ave>>(emptyList())
    val filteredAves: StateFlow<List<Ave>> = _filteredAves.asStateFlow()
    
    private val _availableFamilies = MutableStateFlow<List<String>>(emptyList())
    val availableFamilies: StateFlow<List<String>> = _availableFamilies.asStateFlow()
    
    private val _activeFilter = MutableStateFlow<String>("")
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()
    
    private var searchQuery = ""
    private var currentFilter = ""
    
    fun loadAves(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _avesState.value = UiState.Loading
            try {
                val result = avesRepository.getAves(forceRefresh)
                if (result.isSuccess) {
                    val aves = result.getOrThrow()
                    _avesState.value = UiState.Success(aves)
                    _filteredAves.value = aves
                    
                    // Obtener familias únicas para el filtro
                    val families = aves.mapNotNull { it.familia }.distinct().sorted()
                    _availableFamilies.value = families
                } else {
                    _avesState.value = UiState.Error(message = "Error al cargar aves")
                }
            } catch (e: Exception) {
                _avesState.value = UiState.Error(message = e.message ?: "Error al cargar aves")
            }
        }
    }
    
    fun updateSearch(query: String) {
        searchQuery = query
        applyFilters()
    }
    
    fun setFilter(familia: String) {
        currentFilter = familia
        _activeFilter.value = familia
        applyFilters()
    }
    
    private fun applyFilters() {
        val currentAves = when (val state = _avesState.value) {
            is UiState.Success -> state.data
            else -> return
        }
        
        var filtered = currentAves
        
        // Aplicar filtro de familia
        if (currentFilter.isNotEmpty()) {
            filtered = filtered.filter { it.familia == currentFilter }
        }
        
        // Aplicar búsqueda por texto
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter { ave ->
                ave.nombreComun.contains(searchQuery, ignoreCase = true) ||
                ave.nombreCientifico.contains(searchQuery, ignoreCase = true) ||
                (ave.familia?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
        
        _filteredAves.value = filtered
    }
}