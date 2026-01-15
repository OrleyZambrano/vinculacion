package com.example.vinculacion.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.vinculacion.data.model.UserLocation
import com.example.vinculacion.data.model.Weather
import com.example.vinculacion.data.remote.WeatherApi
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gestiona la obtención de ubicación y clima en tiempo real.
 */
class WeatherRepository(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val weatherApi: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }

    // API Key de OpenWeatherMap (free tier)
    // NOTA: En producción, esto debe estar en gradle.properties o variables de entorno
    private val API_KEY = "bd5e378503939ddaee76f12ad7a97608" // Free tier key para demo

    suspend fun getCurrentLocation(): Result<UserLocation> = withContext(Dispatchers.IO) {
        try {
            // Check permissions
            if (!hasLocationPermission()) {
                return@withContext Result.failure(
                    SecurityException("Se necesita permiso de ubicación para obtener el clima")
                )
            }

            val location = getLastKnownLocation()
            val placeName = getPlaceName(location.latitude, location.longitude)
            
            Result.success(
                UserLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    timestamp = System.currentTimeMillis(),
                    placeName = placeName
                )
            )
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Permisos de ubicación no concedidos"))
        } catch (e: Exception) {
            Result.failure(Exception("No se pudo obtener la ubicación. Verifica que el GPS esté activado."))
        }
    }

    suspend fun getCurrentWeather(location: UserLocation): Result<Weather> = withContext(Dispatchers.IO) {
        try {
            val response = weatherApi.getCurrentWeather(
                latitude = location.latitude,
                longitude = location.longitude,
                apiKey = API_KEY
            )

            val weather = Weather(
                temperature = response.main.temp,
                feelsLike = response.main.feels_like,
                condition = response.weather.firstOrNull()?.main ?: "Unknown",
                description = response.weather.firstOrNull()?.description ?: "Sin datos",
                humidity = response.main.humidity,
                windSpeed = response.wind.speed,
                iconCode = response.weather.firstOrNull()?.icon ?: "01d",
                cityName = response.name,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis()
            )

            Result.success(weather)
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("Sin conexión a internet. Verifica tu red."))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Tiempo de espera agotado. Intenta de nuevo."))
        } catch (e: Exception) {
            Result.failure(Exception("No se pudo obtener el clima: ${e.localizedMessage}"))
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission")
    private suspend fun getLastKnownLocation(): Location = suspendCancellableCoroutine { continuation ->
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                continuation.resume(location)
            } else {
                // Fallback to last known location
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                    if (lastLocation != null) {
                        continuation.resume(lastLocation)
                    } else {
                        continuation.resumeWithException(
                            Exception("No se pudo obtener la ubicación")
                        )
                    }
                }.addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
            }
        }.addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }

        continuation.invokeOnCancellation {
            cancellationTokenSource.cancel()
        }
    }

    /**
     * Obtiene el nombre del lugar usando geocodificación inversa
     */
    private suspend fun getPlaceName(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            
            val geocoder = Geocoder(context)
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                // Construir nombre del lugar priorizando información más específica
                when {
                    !address.subLocality.isNullOrBlank() && !address.locality.isNullOrBlank() ->
                        "${address.subLocality}, ${address.locality}"
                    !address.locality.isNullOrBlank() && !address.adminArea.isNullOrBlank() ->
                        "${address.locality}, ${address.adminArea}"
                    !address.locality.isNullOrBlank() ->
                        address.locality
                    !address.adminArea.isNullOrBlank() ->
                        address.adminArea
                    !address.countryName.isNullOrBlank() ->
                        address.countryName
                    else -> null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            // Si hay error en geocodificación, continuar sin nombre de lugar
            null
        }
    }
}
