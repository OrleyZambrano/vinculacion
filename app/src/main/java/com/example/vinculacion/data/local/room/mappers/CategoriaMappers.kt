package com.example.vinculacion.data.local.room.mappers

import com.example.vinculacion.data.local.room.entities.CategoriaEntity
import java.util.Locale

private val whitespaceRegex = Regex("\\s+")

fun categoriaIdFromFamily(family: String): String = family
    .trim()
    .lowercase(Locale.ROOT)
    .replace(whitespaceRegex, "_")

fun buildCategoriaFromFamily(family: String): CategoriaEntity = CategoriaEntity(
    id = categoriaIdFromFamily(family),
    nombre = family
)
