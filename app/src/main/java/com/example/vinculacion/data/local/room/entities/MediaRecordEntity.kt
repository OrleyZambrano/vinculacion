package com.example.vinculacion.data.local.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_registros",
    foreignKeys = [
        ForeignKey(
            entity = AveEntity::class,
            parentColumns = ["id"],
            childColumns = ["ave_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ave_id"]),
        Index(value = ["sync_status"]),
        Index(value = ["tipo"])
    ]
)
data class MediaRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ave_id") val aveId: Long?,
    @ColumnInfo(name = "tipo") val type: MediaRecordType,
    @ColumnInfo(name = "ruta_local") val localPath: String?,
    @ColumnInfo(name = "ruta_remota") val remoteUrl: String?,
    @ColumnInfo(name = "miniatura_local") val thumbnailPath: String?,
    @ColumnInfo(name = "confianza") val confidence: Float?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    @ColumnInfo(name = "capturado_en") val capturedAt: Long,
    @ColumnInfo(name = "registrado_en") val registeredAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "creado_por") val createdByUserId: String?,
    @ColumnInfo(name = "sync_status") val syncStatus: MediaSyncStatus = MediaSyncStatus.PENDING,
    @ColumnInfo(name = "payload_json") val payloadJson: String? = null
)
