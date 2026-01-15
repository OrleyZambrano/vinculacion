package com.example.vinculacion.data.local.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "tour_participantes",
    primaryKeys = ["tour_id", "usuario_id"],
    foreignKeys = [
        ForeignKey(
            entity = TourEntity::class,
            parentColumns = ["id"],
            childColumns = ["tour_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["estado"])
    ]
)
data class TourParticipantEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "tour_id") val tourId: String,
    @ColumnInfo(name = "usuario_id") val userId: String,
    @ColumnInfo(name = "usuario_nombre") val userName: String?,
    @ColumnInfo(name = "usuario_phone") val userPhone: String?,
    @ColumnInfo(name = "usuario_email") val userEmail: String?,
    @ColumnInfo(name = "estado") val status: TourParticipantStatus = TourParticipantStatus.PENDING,
    @ColumnInfo(name = "solicitado_en") val requestedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "procesado_en") val processedAt: Long? = null,
    @ColumnInfo(name = "actualizado_en") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "notas") val notes: String? = null
)
