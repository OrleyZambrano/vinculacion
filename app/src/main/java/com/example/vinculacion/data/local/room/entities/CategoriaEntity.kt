package com.example.vinculacion.data.local.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Categoría taxonómica o personalizada para agrupar aves.
 */
@Entity(tableName = "categorias")
data class CategoriaEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val descripcion: String? = null,
    val iconName: String? = null,
    val colorHex: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
