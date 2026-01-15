package com.example.vinculacion.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.vinculacion.data.local.room.entities.SyncTaskEntity
import com.example.vinculacion.data.local.room.entities.SyncTaskState

@Dao
interface SyncTasksDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: SyncTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tasks: List<SyncTaskEntity>)

    @Query("SELECT * FROM sync_tasks WHERE state = :state ORDER BY createdAt")
    suspend fun getByState(state: SyncTaskState = SyncTaskState.PENDING): List<SyncTaskEntity>

    @Query("UPDATE sync_tasks SET state = :state, updatedAt = :updatedAt, attemptCount = :attempts, lastAttemptAt = :attemptedAt WHERE id = :id")
    suspend fun updateState(id: Long, state: SyncTaskState, updatedAt: Long = System.currentTimeMillis(), attempts: Int, attemptedAt: Long? = System.currentTimeMillis())

    @Delete
    suspend fun delete(task: SyncTaskEntity)

    @Query("DELETE FROM sync_tasks WHERE state = :state")
    suspend fun deleteByState(state: SyncTaskState)
}
