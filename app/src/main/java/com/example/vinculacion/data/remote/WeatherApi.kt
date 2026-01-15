package com.example.vinculacion.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API de OpenWeatherMap para obtener datos climáticos.
 */
interface WeatherApi {

    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "es"
    ): WeatherResponse
}

data class WeatherResponse(
    val coord: Coord,
    val weather: List<WeatherInfo>,
    val main: Main,
    val wind: Wind,
    val name: String,
    val dt: Long
)

data class Coord(
    val lat: Double,
    val lon: Double
)

data class WeatherInfo(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

data class Main(
    val temp: Double,
    val feels_like: Double,
    val humidity: Int
)

data class Wind(
    val speed: Double
)
