package com.example.vinculacion.data.model

/**
 * Representa la ubicación actual del usuario.
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val timestamp: Long,
    val placeName: String? = null
) {
    fun getCoordinatesString(): String = 
        "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
        
    fun getLocationDescription(): String {
        return when {
            !placeName.isNullOrBlank() -> "$placeName\n📍 ${getCoordinatesString()}"
            else -> "📍 ${getCoordinatesString()}"
        }
    }
}
