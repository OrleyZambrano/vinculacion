package com.example.vinculacion.data.local.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tours",
    indices = [Index(value = ["guia_id"]), Index(value = ["estado"])]
)
data class TourEntity(
    @PrimaryKey val id: String,
    val titulo: String,
    val descripcion: String?,
    @ColumnInfo(name = "guia_id") val guideId: String,
    @ColumnInfo(name = "guia_nombre") val guideName: String?,
    @ColumnInfo(name = "guia_telefono") val guidePhone: String?,
    @ColumnInfo(name = "guia_email") val guideEmail: String?,
    @ColumnInfo(name = "imagen_portada") val coverImageUrl: String?,
    @ColumnInfo(name = "nivel_dificultad") val difficulty: String?,
    @ColumnInfo(name = "estado") val status: TourStatus = TourStatus.DRAFT,
    @ColumnInfo(name = "inicio_epoch") val startTimeEpoch: Long,
    @ColumnInfo(name = "fin_epoch") val endTimeEpoch: Long?,
    @ColumnInfo(name = "punto_encuentro") val meetingPoint: String?,
    @ColumnInfo(name = "punto_encuentro_lat") val meetingPointLat: Double?,
    @ColumnInfo(name = "punto_encuentro_lng") val meetingPointLng: Double?,
    @ColumnInfo(name = "capacidad_max") val capacity: Int?,
    @ColumnInfo(name = "precio_sugerido") val suggestedPrice: Double?,
    @ColumnInfo(name = "ruta_id") val routeId: String?,
    @ColumnInfo(name = "ruta_geojson") val routeGeoJson: String?,
    @ColumnInfo(name = "notas_adicionales") val notes: String?,
    @ColumnInfo(name = "creado_en") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "actualizado_en") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "es_local") val isLocalOnly: Boolean = false
)
