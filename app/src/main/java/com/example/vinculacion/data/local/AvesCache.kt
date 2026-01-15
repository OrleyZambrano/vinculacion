package com.example.vinculacion.data.local

import android.content.Context
import com.example.vinculacion.data.model.Ave
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Almacena el JSON descargado para permitir lectura offline básica.
 */
class AvesCache(private val context: Context, private val gson: Gson = Gson()) {

    private val cacheFileName = "aves_cache.json"

    fun save(aves: List<Ave>) {
        val file = context.getFileStreamPath(cacheFileName)
        file.writeText(gson.toJson(aves))
    }

    fun load(): List<Ave>? {
        val file = context.getFileStreamPath(cacheFileName)
        if (!file.exists() || file.length() == 0L) return null
        val json = file.readText()
        val type = object : TypeToken<List<Ave>>() {}.type
        return gson.fromJson(json, type)
    }
}

