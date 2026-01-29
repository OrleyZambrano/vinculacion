package com.example.vinculacion.data.model

/**
 * Ruta general creada por un guía (no ligada a un tour específico).
 */
data class GuideRoute(
    val id: String,
    val title: String,
    val geoJson: String,
    val guideId: String,
    val createdAt: Long,
    val updatedAt: Long
)
