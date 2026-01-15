package com.example.vinculacion.data.local.room.mappers

import com.example.vinculacion.data.local.room.entities.AveEntity
import com.example.vinculacion.data.model.Ave

private fun String.toCategoryIdOrNull(): String? =
    takeIf { it.isNotBlank() }?.let(::categoriaIdFromFamily)

fun AveEntity.toDomain(): Ave = Ave(
    id = id.toInt(),
    titulo = titulo,
    descripcion = descripcion,
    imagen = imagen,
    familia = familia,
    nombreIngles = nombreIngles.orEmpty(),
    nombreCientifico = nombreCientifico,
    nombreEspanol = nombreEspanol.orEmpty(),
    nombreComun = nombreComun.orEmpty(),
    sonido = sonido.orEmpty()
)

fun Ave.toEntity(categoriaId: String? = familia.toCategoryIdOrNull()): AveEntity = AveEntity(
    id = id.toLong(),
    titulo = titulo,
    descripcion = descripcion,
    imagen = imagen,
    familia = familia,
    nombreIngles = nombreIngles,
    nombreCientifico = nombreCientifico,
    nombreEspanol = nombreEspanol,
    nombreComun = nombreComun,
    sonido = sonido,
    categoriaId = categoriaId,
    popularidad = 0,
    updatedAt = System.currentTimeMillis()
)
