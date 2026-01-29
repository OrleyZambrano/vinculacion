package com.example.vinculacion.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.vinculacion.data.local.room.entities.RouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutesDao {

    @Query("SELECT * FROM routes ORDER BY actualizado_en DESC")
    fun observeRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE guia_id = :guideId ORDER BY actualizado_en DESC")
    fun observeRoutesByGuide(guideId: String): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE guia_id = :guideId ORDER BY actualizado_en DESC")
    suspend fun getRoutesByGuide(guideId: String): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getRoute(id: String): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(route: RouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(routes: List<RouteEntity>)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM routes")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(routes: List<RouteEntity>) {
        clear()
        if (routes.isNotEmpty()) upsert(routes)
    }
}
