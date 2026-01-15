package com.example.vinculacion.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.vinculacion.data.local.room.VinculacionDatabase
import com.example.vinculacion.data.local.room.mappers.toDomain
import com.example.vinculacion.data.local.room.mappers.toEntity
import com.example.vinculacion.data.local.room.mappers.buildCategoriaFromFamily
import com.example.vinculacion.data.model.Ave
import com.example.vinculacion.data.model.Categoria
import com.example.vinculacion.data.remote.AvesApi
import com.example.vinculacion.data.remote.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AvesRepository(context: Context) {

    private val database = VinculacionDatabase.getInstance(context)
    private val avesDao = database.avesDao()
    private val categoriasDao = database.categoriasDao()

    private val api: AvesApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(AppConstants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AvesApi::class.java)
    }

    suspend fun getAves(forceRefresh: Boolean = false): Result<List<Ave>> = withContext(Dispatchers.IO) {
        val cachedEntities = avesDao.getAves()
        val cachedAves = cachedEntities.map { it.toDomain() }
        if (cachedAves.isNotEmpty() && !forceRefresh) {
            return@withContext Result.success(cachedAves)
        }

        return@withContext try {
            val remote = api.getAves()
            val categoryEntities = remote
                .mapNotNull { it.familia.takeIf { family -> family.isNotBlank() } }
                .distinct()
                .map(::buildCategoriaFromFamily)
            val aveEntities = remote.map { it.toEntity() }

            database.withTransaction {
                categoriasDao.clear()
                if (categoryEntities.isNotEmpty()) {
                    categoriasDao.upsertAll(categoryEntities)
                }
                avesDao.replaceAll(aveEntities)
            }
            Result.success(remote)
        } catch (ex: Exception) {
            if (cachedAves.isNotEmpty()) {
                Result.success(cachedAves)
            } else {
                Result.failure(ex)
            }
        }
    }

    fun observeAves(): Flow<List<Ave>> =
        avesDao.observeAves().map { list -> list.map { it.toDomain() } }

    fun observeCategorias(): Flow<List<Categoria>> =
        categoriasDao.observeCategorias().map { list -> list.map { it.toDomain() } }

    fun observeTopAves(limit: Int): Flow<List<Ave>> =
        avesDao.observeTopAves(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getCategorias(): List<Categoria> = withContext(Dispatchers.IO) {
        categoriasDao.getCategorias().map { it.toDomain() }
    }

    suspend fun getCachedAves(): List<Ave> = withContext(Dispatchers.IO) {
        avesDao.getAves().map { it.toDomain() }
    }

    suspend fun increasePopularity(aveId: Long, increment: Int = 1) = withContext(Dispatchers.IO) {
        avesDao.increasePopularity(aveId, increment)
    }
}

