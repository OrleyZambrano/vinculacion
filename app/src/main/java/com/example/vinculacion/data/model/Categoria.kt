package com.example.vinculacion.data.model

/**
 * Representa una categoría taxonómica o temática para agrupar aves.
 */
data class Categoria(
    val id: String,
    val nombre: String,
    val descripcion: String?,
    val iconName: String?,
    val colorHex: String?
)
