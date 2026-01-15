package com.example.vinculacion.data.repository

import android.content.Context
import com.example.vinculacion.data.local.room.VinculacionDatabase
import com.example.vinculacion.data.local.room.entities.SyncTaskEntity
import com.example.vinculacion.data.local.room.entities.SyncTaskState
import com.example.vinculacion.data.local.room.mappers.toDomain
import com.example.vinculacion.data.local.room.mappers.toEntity
import com.example.vinculacion.data.model.SyncTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncRepository(context: Context) {

    private val database = VinculacionDatabase.getInstance(context)
    private val syncTasksDao = database.syncTasksDao()

    suspend fun enqueue(task: SyncTask): Long = withContext(Dispatchers.IO) {
        syncTasksDao.upsert(task.toEntity())
    }

    suspend fun enqueue(type: String, payloadId: String?, payloadJson: String): Long = withContext(Dispatchers.IO) {
        val entity = SyncTaskEntity(
            payloadType = type,
            payloadId = payloadId,
            payloadJson = payloadJson
        )
        syncTasksDao.upsert(entity)
    }

    suspend fun getPending(): List<SyncTask> = withContext(Dispatchers.IO) {
        syncTasksDao.getByState(SyncTaskState.PENDING).map { it.toDomain() }
    }

    suspend fun markRunning(id: Long, attempts: Int) = withContext(Dispatchers.IO) {
        syncTasksDao.updateState(id, SyncTaskState.RUNNING, attempts = attempts)
    }

    suspend fun markCompleted(id: Long) = withContext(Dispatchers.IO) {
        syncTasksDao.updateState(id, SyncTaskState.COMPLETED, attempts = 0)
    }

    suspend fun markFailed(id: Long, attempts: Int) = withContext(Dispatchers.IO) {
        syncTasksDao.updateState(id, SyncTaskState.FAILED, attempts = attempts)
    }

    suspend fun delete(task: SyncTask) = withContext(Dispatchers.IO) {
        syncTasksDao.delete(task.toEntity())
    }

    suspend fun clearCompleted() = withContext(Dispatchers.IO) {
        syncTasksDao.deleteByState(SyncTaskState.COMPLETED)
    }
}
