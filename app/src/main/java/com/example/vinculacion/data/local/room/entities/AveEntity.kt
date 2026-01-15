package com.example.vinculacion.data.local.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "aves",
    foreignKeys = [
        ForeignKey(
            entity = CategoriaEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoria_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["categoria_id"]),
        Index(value = ["nombre_cientifico"], unique = true),
        Index(value = ["popularidad"])
    ]
)
data class AveEntity(
    @PrimaryKey val id: Long,
    val titulo: String,
    val descripcion: String,
    val imagen: String,
    val familia: String,
    @ColumnInfo(name = "nombre_ingles") val nombreIngles: String?,
    @ColumnInfo(name = "nombre_cientifico") val nombreCientifico: String,
    @ColumnInfo(name = "nombre_espanol") val nombreEspanol: String?,
    @ColumnInfo(name = "nombre_comun") val nombreComun: String?,
    val sonido: String?,
    @ColumnInfo(name = "categoria_id") val categoriaId: String?,
    @ColumnInfo(name = "popularidad") val popularidad: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
