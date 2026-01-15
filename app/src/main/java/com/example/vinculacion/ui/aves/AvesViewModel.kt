package com.example.vinculacion.ui.aves

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.vinculacion.data.model.Ave
import com.example.vinculacion.data.repository.AvesRepository
import com.example.vinculacion.ui.common.UiState
import kotlinx.coroutines.launch

class AvesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AvesRepository(application.applicationContext)

    val avesState = MutableLiveData<UiState<List<Ave>>>()
    val filteredAves = MutableLiveData<List<Ave>>()
    val activeFilter = MutableLiveData<String?>(null)
    private var lastResult: List<Ave> = emptyList()
    private var currentQuery: String = ""

    fun loadAves(forceRefresh: Boolean = false) {
        if (avesState.value is UiState.Loading) return
        avesState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getAves(forceRefresh)
            avesState.value = result.fold(
                onSuccess = {
                    lastResult = it
                    applyFilters()
                    if (it.isEmpty()) {
                        UiState.Empty()
                    } else {
                        UiState.Success(it)
                    }
                },
                onFailure = { UiState.Error(it) }
            )
        }
    }

    fun updateSearch(query: String) {
        currentQuery = query
        applyFilters()
    }

    fun setFilter(filter: String?) {
        activeFilter.value = filter
        applyFilters()
    }

    private fun applyFilters() {
        val filter = activeFilter.value
        val filtered = lastResult.filter { ave ->
            val matchesQuery = currentQuery.isBlank() || ave.titulo.contains(currentQuery, ignoreCase = true) || ave.nombreComun.contains(currentQuery, ignoreCase = true)
            val matchesFilter = filter.isNullOrBlank() || ave.familia.equals(filter, ignoreCase = true)
            matchesQuery && matchesFilter
        }
        filteredAves.value = filtered
    }

    fun availableFamilies(): List<String> = lastResult.map { it.familia }.distinct().sorted()
}
