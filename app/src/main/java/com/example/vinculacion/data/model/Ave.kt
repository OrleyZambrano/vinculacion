package com.example.vinculacion.data.model

import com.example.vinculacion.data.remote.AppConstants
import com.google.gson.annotations.SerializedName

/**
 * Representa la información principal de un ave disponible en el dataset remoto.
 */
data class Ave(
    val id: Int,
    val titulo: String,
    val descripcion: String,
    val imagen: String,
    val familia: String,
    @SerializedName("nombre_ingles") val nombreIngles: String,
    @SerializedName("nombre_cientifico") val nombreCientifico: String,
    @SerializedName("nombre_espanol") val nombreEspanol: String,
    @SerializedName("nombre_comun") val nombreComun: String,
    val sonido: String
) {
    fun imageUrl(): String {
        // Si la imagen está vacía o en blanco, retornar string vacío para que Glide use el placeholder
        return if (imagen.isNotBlank()) {
            "${AppConstants.BASE_URL}${AppConstants.IMAGES_PATH}$imagen"
        } else {
            ""
        }
    }
    
    fun soundUrl(): String = sonido
}

