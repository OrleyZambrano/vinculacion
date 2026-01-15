package com.example.vinculacion.data.local.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_tasks",
    indices = [Index(value = ["state"]), Index(value = ["payload_type"])]
)
data class SyncTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "payload_type") val payloadType: String,
    @ColumnInfo(name = "payload_id") val payloadId: String?,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val state: SyncTaskState = SyncTaskState.PENDING
)
