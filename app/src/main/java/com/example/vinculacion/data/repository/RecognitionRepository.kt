package com.example.vinculacion.data.repository

import android.content.Context
import com.example.vinculacion.data.local.room.entities.MediaRecordType
import com.example.vinculacion.data.model.Ave
import com.example.vinculacion.data.model.MediaRecordDraft
import com.example.vinculacion.data.recognition.BirdRecognitionManager
import com.example.vinculacion.data.recognition.RecognitionMatch
import com.example.vinculacion.data.recognition.RecognitionRequest
import com.example.vinculacion.data.recognition.RecognitionResult
import com.example.vinculacion.data.recognition.RecognitionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Coordina el proceso de reconocimiento y persistencia de registros multimedia.
 */
class RecognitionRepository private constructor(
    private val recognitionManager: BirdRecognitionManager,
    private val avesRepository: AvesRepository,
    private val mediaRepository: MediaRepository,
    private val syncRepository: SyncRepository
) {

    suspend fun recognizeAndStore(request: RecognitionRequest, persistMedia: Boolean = true): RecognitionResult {
        val result = recognitionManager.recognize(request)
        if (!persistMedia) {
            return result
        }
        val aves = avesRepository.getCachedAves()
        val bestMatch = result.matches.maxByOrNull { it.confidence }
        val resolvedAveId = resolveAveId(bestMatch, aves, request.hintScientificName)
        val recordType = if (request.source == RecognitionSource.IMAGE) MediaRecordType.PHOTO else MediaRecordType.AUDIO
        val payload = buildPayloadJson(request, result, bestMatch)
        val draft = MediaRecordDraft(
            aveId = resolvedAveId,
            type = recordType,
            localPath = request.file.absolutePath,
            confidence = bestMatch?.confidence,
            latitude = request.latitude,
            longitude = request.longitude,
            altitude = request.altitude,
            capturedAt = request.capturedAt,
            createdByUserId = request.userId,
            payloadJson = payload
        )
        val stored = mediaRepository.saveDraft(draft)
        runCatching { mediaRepository.pushRecordToRemote(stored) }
        resolvedAveId ?: stored.aveId
            ?.let { avesRepository.increasePopularity(it, increment = 1) }
        enqueueSyncTask(stored.id, recordType)
        return result.copy(savedRecordId = stored.id)
    }

    private suspend fun enqueueSyncTask(recordId: Long, type: MediaRecordType) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("recordId", recordId)
            put("type", type.name)
        }
        syncRepository.enqueue(
            type = "media_record",
            payloadId = recordId.toString(),
            payloadJson = payload.toString()
        )
    }

    private fun buildPayloadJson(request: RecognitionRequest, result: RecognitionResult, bestMatch: RecognitionMatch?): String {
        val json = JSONObject().apply {
            put("requestId", request.id)
            put("source", request.source.name)
            put("capturedAt", request.capturedAt)
            put("processedAt", result.metadata.processedAt)
            put("durationMs", result.metadata.durationMs)
            put("latitude", request.latitude)
            put("longitude", request.longitude)
            put("altitude", request.altitude)
            put("accuracyMeters", request.accuracyMeters)
            put("userId", request.userId)
            put("hintScientificName", request.hintScientificName)
            put("notes", result.notes)
            put("matches", JSONArray().apply {
                result.matches.forEach { match ->
                    put(JSONObject().apply {
                        put("label", match.label)
                        put("scientificName", match.scientificName)
                        put("aveId", match.aveId)
                        put("confidence", match.confidence)
                        put("source", match.source.name)
                    })
                }
            })
            bestMatch?.let {
                put("bestMatch", JSONObject().apply {
                    put("label", it.label)
                    put("scientificName", it.scientificName)
                    put("confidence", it.confidence)
                })
            }
        }
        return json.toString()
    }

    private fun resolveAveId(bestMatch: RecognitionMatch?, aves: List<Ave>, hint: String?): Long? {
        bestMatch ?: return hint?.let { findAveByScientificName(it, aves) }
        bestMatch.aveId?.let { return it }
        val candidates = listOfNotNull(bestMatch.scientificName, bestMatch.label, hint)
        candidates.forEach { candidate ->
            findAveByScientificName(candidate, aves)?.let { return it }
            findAveByCommonName(candidate, aves)?.let { return it }
        }
        return null
    }

    private fun findAveByScientificName(name: String, aves: List<Ave>): Long? {
        val normalized = normalize(name)
        return aves.firstOrNull { normalize(it.nombreCientifico) == normalized || normalize(it.titulo) == normalized }?.id?.toLong()
    }

    private fun findAveByCommonName(name: String, aves: List<Ave>): Long? {
        val normalized = normalize(name)
        return aves.firstOrNull {
            normalize(it.nombreComun) == normalized || normalize(it.nombreEspanol) == normalized || normalize(it.nombreIngles) == normalized
        }?.id?.toLong()
    }

    private fun normalize(value: String?): String = value?.lowercase(Locale.ROOT)?.trim().orEmpty()

    companion object {
        fun create(context: Context): RecognitionRepository {
            val manager = BirdRecognitionManager.create(context)
            val avesRepo = AvesRepository(context)
            val mediaRepo = MediaRepository(context)
            val syncRepo = SyncRepository(context)
            return RecognitionRepository(manager, avesRepo, mediaRepo, syncRepo)
        }
    }
}
