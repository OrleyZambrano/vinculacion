package com.example.vinculacion.ui.map

import com.example.vinculacion.data.model.MediaRecord
import com.example.vinculacion.data.model.Tour
import com.google.android.gms.maps.model.LatLng

/**
 * Representa la ruta de un tour lista para dibujar en el mapa.
 */
data class TourRoute(
    val tour: Tour,
    val path: List<LatLng>,
    val waypoints: List<LatLng>,
    val meetingPoint: LatLng?
)

/**
 * Wrapper con los puntos disponibles para generar el mapa de calor.
 */
data class HeatmapData(
    val captures: List<MediaRecord>,
    val points: List<LatLng>
)
