package com.example.vinculacion.data.local.room.mappers

import com.example.vinculacion.data.local.room.entities.CategoriaEntity
import com.example.vinculacion.data.model.Categoria

fun CategoriaEntity.toDomain(): Categoria = Categoria(
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    iconName = iconName,
    colorHex = colorHex
)

fun Categoria.toEntity(): CategoriaEntity = CategoriaEntity(
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    iconName = iconName,
    colorHex = colorHex
)
