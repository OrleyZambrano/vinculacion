package com.example.vinculacion.data.model

/**
 * Representa las condiciones climáticas actuales.
 */
data class Weather(
    val temperature: Double,
    val feelsLike: Double,
    val condition: String,
    val description: String,
    val humidity: Int,
    val windSpeed: Double,
    val iconCode: String,
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
) {
    val temperatureCelsius: Int get() = temperature.toInt()
    val windSpeedKmh: Int get() = (windSpeed * 3.6).toInt()
    
    fun getIconUrl(): String {
        // OpenWeatherMap icons
        return "https://openweathermap.org/img/wn/${iconCode}@2x.png"
    }
    
    fun getBirdActivityLevel(): String = when {
        temperature < 5 -> "Baja actividad - Muy frío"
        temperature in 5.0..15.0 -> "Actividad moderada"
        temperature in 15.0..25.0 -> "Buen momento para ver aves"
        temperature in 25.0..32.0 -> "Actividad moderada - Buscar sombra"
        else -> "Baja actividad - Calor extremo"
    }
}
