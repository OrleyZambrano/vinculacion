package com.example.vinculacion.data.remote

import com.google.gson.annotations.SerializedName
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
    val name: String,
    val coord: Coord,
    val main: Main,
    val weather: List<WeatherInfo>,
    val wind: Wind
)

data class Coord(
    val lat: Double,
    val lon: Double
)

data class Main(
    val temp: Double,
    @SerializedName("feels_like") val feelsLike: Double,
    val humidity: Int
)

data class WeatherInfo(
    val main: String,
    val description: String,
    val icon: String
)

data class Wind(
    val speed: Double
)
