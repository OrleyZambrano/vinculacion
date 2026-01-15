package com.example.vinculacion.ui.map

import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject

object RouteParser {

    fun parseLineString(geoJson: String?): List<LatLng> {
        if (geoJson.isNullOrBlank()) return emptyList()
        return try {
            val jsonObject = JSONObject(geoJson)
            val coordinates = when (jsonObject.optString("type")) {
                "LineString" -> jsonObject.optJSONArray("coordinates")
                "Feature" -> jsonObject.optJSONObject("geometry")?.optJSONArray("coordinates")
                "FeatureCollection" -> {
                    val features = jsonObject.optJSONArray("features")
                    val firstGeometry = features?.optJSONObject(0)?.optJSONObject("geometry")
                    firstGeometry?.optJSONArray("coordinates")
                }
                else -> null
            }
            coordinates?.toLatLngList() ?: emptyList()
        } catch (_: Exception) {
            parseLegacyFormat(geoJson)
        }
    }

    private fun parseLegacyFormat(raw: String): List<LatLng> {
        return try {
            val jsonArray = JSONArray(raw)
            jsonArray.toLatLngList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JSONArray.toLatLngList(): List<LatLng> {
        val result = mutableListOf<LatLng>()
        for (i in 0 until length()) {
            when (val entry = get(i)) {
                is JSONArray -> {
                    if (entry.length() >= 2) {
                        val lng = entry.optDouble(0)
                        val lat = entry.optDouble(1)
                        result.add(LatLng(lat, lng))
                    }
                }
                is JSONObject -> {
                    val lat = entry.optDouble("lat")
                    val lng = entry.optDouble("lng")
                    result.add(LatLng(lat, lng))
                }
            }
        }
        return result
    }
}
