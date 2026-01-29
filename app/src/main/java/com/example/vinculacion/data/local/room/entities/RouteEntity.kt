package com.example.vinculacion.data.local.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routes",
    indices = [Index(value = ["guia_id"]) ]
)
data class RouteEntity(
    @PrimaryKey val id: String,
    val titulo: String,
    @ColumnInfo(name = "geo_json") val geoJson: String,
    @ColumnInfo(name = "guia_id") val guideId: String,
    @ColumnInfo(name = "creado_en") val createdAt: Long,
    @ColumnInfo(name = "actualizado_en") val updatedAt: Long
)
