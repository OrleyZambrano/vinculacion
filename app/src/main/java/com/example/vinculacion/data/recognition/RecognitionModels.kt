package com.example.vinculacion.data.recognition

import java.io.File
import java.util.UUID

/**
 * Representa la fuente del reconocimiento (imagen o audio).
 */
enum class RecognitionSource { IMAGE, AUDIO }

/**
 * Solicitud de reconocimiento que incluye la ruta local del archivo y metadatos de captura.
 */
data class RecognitionRequest(
    val id: String = UUID.randomUUID().toString(),
    val file: File,
    val source: RecognitionSource,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val accuracyMeters: Float? = null,
    val capturedAt: Long,
    val userId: String? = null,
    val hintScientificName: String? = null,
    val topK: Int = RecognitionModelsConfig.DEFAULT_TOP_K,
    val confidenceThreshold: Float = RecognitionModelsConfig.DEFAULT_CONFIDENCE_THRESHOLD
)

/**
 * Resultado individual que asocia el modelo con una especie.
 */
data class RecognitionMatch(
    val label: String,
    val scientificName: String?,
    val aveId: Long?,
    val confidence: Float,
    val source: RecognitionSource
)

/**
 * Metadatos capturados durante la inferencia para auditar registros.
 */
data class RecognitionMetadata(
    val capturedAt: Long,
    val processedAt: Long,
    val durationMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val accuracyMeters: Float?,
    val userId: String?
)

/**
 * Resultado global del reconocimiento.
 */
data class RecognitionResult(
    val requestId: String,
    val matches: List<RecognitionMatch>,
    val metadata: RecognitionMetadata,
    val savedRecordId: Long? = null,
    val notes: String? = null
)
