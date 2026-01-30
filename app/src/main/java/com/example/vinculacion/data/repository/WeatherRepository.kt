package com.example.vinculacion.data.repository

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
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
 * Gestiona la obtención de ubicación y clima en tiempo real con persistencia local.
 */
class WeatherRepository(private val context: Context) {

    private companion object {
        const val TAG = "Weather"
        const val CACHE_DURATION_MS = 10 * 60 * 1000L // 10 minutos
        const val NETWORK_TIMEOUT_MS = 10_000L // 10 segundos
        const val PREFS_NAME = "weather_prefs"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_FEELS_LIKE = "feels_like"
        const val KEY_CONDITION = "condition"
        const val KEY_DESCRIPTION = "description"
        const val KEY_HUMIDITY = "humidity"
        const val KEY_WIND_SPEED = "wind_speed"
        const val KEY_ICON_CODE = "icon_code"
        const val KEY_CITY_NAME = "city_name"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_TIMESTAMP = "timestamp"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val weatherApi: WeatherApi by lazy {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(NETWORK_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(NETWORK_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(NETWORK_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }

    private val apiKey: String by lazy {
        context.getString(R.string.weather_api_key)
    }

    // Cache simple en memoria
    private var cachedWeather: Weather? = null
    private var cacheTimestamp: Long = 0L
    private var isLoadingWeather = false

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
            // Verificar cache válido en memoria
            val now = System.currentTimeMillis()
            if (cachedWeather != null && (now - cacheTimestamp) < CACHE_DURATION_MS) {
                Log.d(TAG, "Returning cached weather (age: ${(now - cacheTimestamp) / 1000}s)")
                return@withContext Result.success(cachedWeather!!)
            }

            // Evitar múltiples llamadas simultáneas
            if (isLoadingWeather) {
                Log.d(TAG, "Weather request already in progress, waiting...")
                kotlinx.coroutines.delay(500)
                if (cachedWeather != null) {
                    return@withContext Result.success(cachedWeather!!)
                }
            }

            isLoadingWeather = true

            if (apiKey.isBlank() || apiKey == "REPLACE_WITH_YOUR_KEY") {
                Log.e(TAG, "Weather API key missing or placeholder")
                isLoadingWeather = false
                // Intentar cargar desde almacenamiento local
                val savedWeather = loadWeatherFromStorage()
                return@withContext if (savedWeather != null) {
                    Log.d(TAG, "Returning saved weather from storage (offline)")
                    Result.success(savedWeather)
                } else {
                    Result.failure(Exception("Configura la API key del clima"))
                }
            }

            Log.d(TAG, "Requesting weather from OpenWeatherMap for lat=${location.latitude}, lon=${location.longitude}")
            val response = weatherApi.getCurrentWeather(
                latitude = location.latitude,
                longitude = location.longitude,
                apiKey = apiKey
            )

            Log.d(TAG, "Weather response: temp=${response.main.temp}, city=${response.name}")

            val weather = Weather(
                temperature = response.main.temp,
                feelsLike = response.main.feelsLike,
                condition = response.weather.firstOrNull()?.main ?: "Desconocido",
                description = response.weather.firstOrNull()?.description ?: "Sin descripción",
                humidity = response.main.humidity,
                windSpeed = response.wind.speed,
                iconCode = response.weather.firstOrNull()?.icon ?: "01d",
                cityName = response.name,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis()
            )

            // Guardar en cache de memoria
            cachedWeather = weather
            cacheTimestamp = System.currentTimeMillis()
            
            // Guardar en almacenamiento persistente
            saveWeatherToStorage(weather)
            
            isLoadingWeather = false

            Result.success(weather)
        } catch (e: HttpException) {
            isLoadingWeather = false
            val errorBody = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            Log.e(TAG, "Weather HTTP error code=${e.code()} message=${e.message()} body=${errorBody}")
            val message = when (e.code()) {
                401 -> {
                    // Intentar cargar datos guardados cuando API key no está activada
                    val savedWeather = loadWeatherFromStorage()
                    if (savedWeather != null) {
                        Log.d(TAG, "API key inactiva, usando datos guardados")
                        return@withContext Result.success(savedWeather)
                    }
                    "API key del clima inactiva. Las claves nuevas pueden tardar hasta 2 horas en activarse."
                }
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
            isLoadingWeather = false
            Log.e(TAG, "Weather network error: UnknownHost (Sin internet)", e)
            // Intentar cargar desde almacenamiento local cuando no hay internet
            val savedWeather = loadWeatherFromStorage()
            return@withContext if (savedWeather != null) {
                Log.d(TAG, "Returning saved weather from storage (offline)")
                Result.success(savedWeather)
            } else {
                Result.failure(Exception("Sin conexión a internet. No hay datos guardados."))
            }
        } catch (e: java.net.SocketTimeoutException) {
            isLoadingWeather = false
            Log.e(TAG, "Weather network error: Timeout", e)
            // Intentar cargar desde almacenamiento local
            val savedWeather = loadWeatherFromStorage()
            return@withContext if (savedWeather != null) {
                Log.d(TAG, "Returning saved weather from storage (timeout)")
                Result.success(savedWeather)
            } else {
                Result.failure(Exception("Tiempo de espera agotado. Intenta de nuevo."))
            }
        } catch (e: Exception) {
            isLoadingWeather = false
            Log.e(TAG, "Weather unexpected error", e)
            // Intentar cargar desde almacenamiento local como último recurso
            val savedWeather = loadWeatherFromStorage()
            return@withContext if (savedWeather != null) {
                Log.d(TAG, "Returning saved weather from storage (error fallback)")
                Result.success(savedWeather)
            } else {
                Result.failure(Exception(e.localizedMessage ?: "No se pudo obtener el clima"))
            }
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

    /**
     * Guarda los datos del clima en SharedPreferences
     */
    private fun saveWeatherToStorage(weather: Weather) {
        try {
            prefs.edit().apply {
                putString(KEY_TEMPERATURE, weather.temperature.toString())
                putString(KEY_FEELS_LIKE, weather.feelsLike.toString())
                putString(KEY_CONDITION, weather.condition)
                putString(KEY_DESCRIPTION, weather.description)
                putInt(KEY_HUMIDITY, weather.humidity)
                putString(KEY_WIND_SPEED, weather.windSpeed.toString())
                putString(KEY_ICON_CODE, weather.iconCode)
                putString(KEY_CITY_NAME, weather.cityName)
                putString(KEY_LATITUDE, weather.latitude.toString())
                putString(KEY_LONGITUDE, weather.longitude.toString())
                putLong(KEY_TIMESTAMP, weather.timestamp)
                apply()
            }
            Log.d(TAG, "Weather saved to storage successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving weather to storage", e)
        }
    }

    /**
     * Carga los datos del clima desde SharedPreferences
     */
    private fun loadWeatherFromStorage(): Weather? {
        return try {
            val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
            if (timestamp == 0L) {
                Log.d(TAG, "No weather data found in storage")
                return null
            }

            val weather = Weather(
                temperature = prefs.getString(KEY_TEMPERATURE, null)?.toDoubleOrNull() ?: return null,
                feelsLike = prefs.getString(KEY_FEELS_LIKE, null)?.toDoubleOrNull() ?: return null,
                condition = prefs.getString(KEY_CONDITION, null) ?: return null,
                description = prefs.getString(KEY_DESCRIPTION, null) ?: return null,
                humidity = prefs.getInt(KEY_HUMIDITY, 0),
                windSpeed = prefs.getString(KEY_WIND_SPEED, null)?.toDoubleOrNull() ?: return null,
                iconCode = prefs.getString(KEY_ICON_CODE, null) ?: return null,
                cityName = prefs.getString(KEY_CITY_NAME, null) ?: return null,
                latitude = prefs.getString(KEY_LATITUDE, null)?.toDoubleOrNull() ?: return null,
                longitude = prefs.getString(KEY_LONGITUDE, null)?.toDoubleOrNull() ?: return null,
                timestamp = timestamp
            )

            Log.d(TAG, "Weather loaded from storage (saved ${(System.currentTimeMillis() - timestamp) / 1000}s ago)")
            weather
        } catch (e: Exception) {
            Log.e(TAG, "Error loading weather from storage", e)
            null
        }
    }}