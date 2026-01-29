package com.example.vinculacion.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.vinculacion.R
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
import retrofit2.HttpException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gestiona la obtención de ubicación y clima en tiempo real.
 */
class WeatherRepository(private val context: Context) {

    private companion object {
        const val TAG = "Weather"
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val weatherApi: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.weatherapi.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }

    private val apiKey: String by lazy {
        context.getString(R.string.weather_api_key)
    }

    suspend fun getCurrentLocation(): Result<UserLocation> = withContext(Dispatchers.IO) {
        try {
            // Check permissions
            if (!hasLocationPermission()) {
                Log.e(TAG, "Location permission missing")
                return@withContext Result.failure(
                    SecurityException("Se necesita permiso de ubicación para obtener el clima")
                )
            }

            val location = getLastKnownLocation()
            val placeName = getPlaceName(location.latitude, location.longitude)

            Log.d(TAG, "Location obtained lat=${location.latitude}, lon=${location.longitude}, accuracy=${location.accuracy}")
            
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
            Log.e(TAG, "Location permission error", e)
            Result.failure(SecurityException("Permisos de ubicación no concedidos"))
        } catch (e: Exception) {
            Log.e(TAG, "Location error", e)
            Result.failure(Exception("No se pudo obtener la ubicación. Verifica que el GPS esté activado."))
        }
    }

    suspend fun getCurrentWeather(location: UserLocation): Result<Weather> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiKey == "REPLACE_WITH_YOUR_KEY") {
                Log.e(TAG, "Weather API key missing or placeholder")
                return@withContext Result.failure(Exception("Configura la API key del clima"))
            }
            Log.d(TAG, "Requesting weather for lat=${location.latitude}, lon=${location.longitude}")
            val response = weatherApi.getCurrentWeather(
                apiKey = apiKey,
                query = "${location.latitude},${location.longitude}"
            )

            Log.d(TAG, "Weather response: tempC=${response.current.tempC}, city=${response.location.name}")

            val weather = Weather(
                temperature = response.current.tempC,
                feelsLike = response.current.feelsLikeC,
                condition = response.current.condition.text,
                description = response.current.condition.text,
                humidity = response.current.humidity,
                windSpeed = response.current.windKph / 3.6,
                iconCode = response.current.condition.icon,
                cityName = response.location.name,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis()
            )

            Result.success(weather)
        } catch (e: HttpException) {
            val errorBody = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            Log.e(TAG, "Weather HTTP error code=${e.code()} message=${e.message()} body=${errorBody}")
            val message = when (e.code()) {
                401 -> "API key del clima inválida"
                429 -> "Límite de solicitudes de clima excedido"
                else -> {
                    if (!errorBody.isNullOrBlank()) {
                        "Error ${e.code()}: ${errorBody}"
                    } else {
                        "No se pudo obtener el clima"
                    }
                }
            }
            Result.failure(Exception(message))
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Weather network error: UnknownHost", e)
            Result.failure(Exception("Sin conexión a internet. Verifica tu red."))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Weather network error: Timeout", e)
            Result.failure(Exception("Tiempo de espera agotado. Intenta de nuevo."))
        } catch (e: Exception) {
            Log.e(TAG, "Weather unexpected error", e)
            Result.failure(Exception(e.localizedMessage ?: "No se pudo obtener el clima"))
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
            @Suppress("DEPRECATION")
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
