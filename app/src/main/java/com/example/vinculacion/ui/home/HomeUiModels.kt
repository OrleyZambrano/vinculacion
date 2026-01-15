package com.example.vinculacion.ui.home

import androidx.annotation.DrawableRes
import com.example.vinculacion.data.model.Ave
import com.example.vinculacion.data.model.Categoria

/**
 * Modelo para representar las secciones clave del home.
 */
data class HomeUiData(
    val topBirds: List<Ave>,
    val categories: List<Categoria>,
    val quickActions: List<HomeQuickAction>
)

/**
 * Acciones rápidas visibles en la pantalla principal.
 */
data class HomeQuickAction(
    @DrawableRes val iconRes: Int,
    val title: String,
    val subtitle: String,
    val action: HomeAction
)

enum class HomeAction { CATEGORIES, TOURS, MAP, RECOGNITION, PROFILE }
