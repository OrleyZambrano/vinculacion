package com.example.vinculacion.data.repository

import android.content.Context
import android.util.Log
import com.example.vinculacion.BuildConfig
import com.example.vinculacion.data.local.room.VinculacionDatabase
import com.example.vinculacion.data.local.room.mappers.toDomain
import com.example.vinculacion.data.local.room.mappers.toEntity
import com.example.vinculacion.data.model.GuideRoute
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RoutesRepository(context: Context) {

    private val database = VinculacionDatabase.getInstance(context)
    private val routesDao = database.routesDao()

    private val firestore = FirebaseFirestore.getInstance()
    private val routesCollection = firestore.collection("routes")

    fun observeRoutes(): Flow<List<GuideRoute>> =
        routesDao.observeRoutes().map { list -> list.map { it.toDomain() } }

    fun observeRoutesByGuide(guideId: String): Flow<List<GuideRoute>> =
        routesDao.observeRoutesByGuide(guideId).map { list -> list.map { it.toDomain() } }

    suspend fun getRoutesByGuide(guideId: String): List<GuideRoute> = withContext(Dispatchers.IO) {
        routesDao.getRoutesByGuide(guideId).map { it.toDomain() }
    }

    suspend fun createRoute(route: GuideRoute): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val data = mapOf(
                "id" to route.id,
                "title" to route.title,
                "geoJson" to route.geoJson,
                "guideId" to route.guideId,
                "createdAt" to route.createdAt,
                "updatedAt" to route.updatedAt
            )
            routesCollection.document(route.id).set(data).await()
            routesDao.upsert(route.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRoute(routeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            routesCollection.document(routeId).delete().await()
            routesDao.delete(routeId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRoute(route: GuideRoute): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val data = mapOf(
                "title" to route.title,
                "geoJson" to route.geoJson,
                "updatedAt" to route.updatedAt
            )
            routesCollection.document(route.id).update(data).await()
            routesDao.upsert(route.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncFromRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val snapshot = routesCollection.get().await()
            val routes = snapshot.documents.mapNotNull { it.toDomainRoute() }
            routesDao.replaceAll(routes.map { it.toEntity() })
            Result.success(Unit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("RoutesRepository", "Error syncing routes", e)
            }
            Result.failure(e)
        }
    }

    private fun DocumentSnapshot.toDomainRoute(): GuideRoute? {
        val routeId = getString("id") ?: id
        val title = getString("title") ?: return null
        val geoJson = getString("geoJson") ?: return null
        val guideId = getString("guideId") ?: return null
        val createdAt = readEpoch("createdAt") ?: System.currentTimeMillis()
        val updatedAt = readEpoch("updatedAt") ?: createdAt
        return GuideRoute(
            id = routeId,
            title = title,
            geoJson = geoJson,
            guideId = guideId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun DocumentSnapshot.readEpoch(field: String): Long? {
        return getLong(field)
            ?: getTimestamp(field)?.toDate()?.time
    }
}
