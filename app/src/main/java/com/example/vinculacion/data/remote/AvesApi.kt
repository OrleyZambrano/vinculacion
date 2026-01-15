package com.example.vinculacion.data.remote

import com.example.vinculacion.data.model.Ave
import retrofit2.http.GET

/**
 * API declarativa para obtener el listado de aves desde el JSON remoto.
 */
interface AvesApi {
    @GET("aves.json")
    suspend fun getAves(): List<Ave>
}

