package com.example.vinculacion.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API de WeatherAPI.com para obtener datos climáticos.
 */
interface WeatherApi {

    @GET("current.json")
    suspend fun getCurrentWeather(
        @Query("key") apiKey: String,
        @Query("q") query: String,
        @Query("lang") lang: String = "es"
    ): WeatherResponse
}

data class WeatherResponse(
    val location: WeatherLocation,
    val current: WeatherCurrent
)

data class WeatherLocation(
    val name: String,
    val lat: Double,
    val lon: Double
)

data class WeatherCurrent(
    @SerializedName("temp_c") val tempC: Double,
    @SerializedName("feelslike_c") val feelsLikeC: Double,
    val humidity: Int,
    @SerializedName("wind_kph") val windKph: Double,
    val condition: WeatherCondition
)

data class WeatherCondition(
    val text: String,
    val icon: String
)
