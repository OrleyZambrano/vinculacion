package com.example.vinculacion.data.recognition

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.File

/**
 * Implementación basada en TensorFlow Lite para reconocimiento por imagen.
 */
class TFLiteBirdImageRecognizer(
    private val context: Context,
    private val modelPath: String = RecognitionModelsConfig.DEFAULT_IMAGE_MODEL_PATH
) : BirdRecognitionEngine {

    private val classifier: ImageClassifier? by lazy {
        try {
            val baseOptions = BaseOptions.builder().setNumThreads(4).build()
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(RecognitionModelsConfig.DEFAULT_TOP_K)
                .setScoreThreshold(RecognitionModelsConfig.DEFAULT_CONFIDENCE_THRESHOLD)
                .build()
            ImageClassifier.createFromFileAndOptions(context, modelPath, options)
        } catch (ex: Exception) {
            null
        }
    }

    override suspend fun recognize(request: RecognitionRequest): RecognitionResult = withContext(Dispatchers.IO) {
        require(request.source == RecognitionSource.IMAGE) { "El reconocedor de imágenes solo acepta solicitudes de tipo IMAGE" }
        val start = SystemClock.elapsedRealtime()
        val classifier = classifier
            ?: return@withContext emptyResult(request, "Modelo de imagen no disponible")

        val bitmap = loadBitmap(request.file)
            ?: return@withContext emptyResult(request, "No se pudo decodificar la imagen")

        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results = classifier.classify(tensorImage)
        val matches = results
            .flatMap { it.categories }
            .sortedByDescending { it.score }
            .take(request.topK)
            .map { category ->
                RecognitionMatch(
                    label = category.label,
                    scientificName = category.label,
                    aveId = null,
                    confidence = category.score,
                    source = RecognitionSource.IMAGE
                )
            }
        val duration = SystemClock.elapsedRealtime() - start
        RecognitionResult(
            requestId = request.id,
            matches = matches,
            metadata = buildMetadata(request, duration)
        )
    }

    private fun loadBitmap(file: File) = try {
        BitmapFactory.decodeFile(file.absolutePath)
    } catch (_: Exception) {
        null
    }

    private fun emptyResult(request: RecognitionRequest, note: String) = RecognitionResult(
        requestId = request.id,
        matches = emptyList(),
        metadata = buildMetadata(request, durationMs = 0L),
        notes = note
    )

    private fun buildMetadata(request: RecognitionRequest, durationMs: Long) = RecognitionMetadata(
        capturedAt = request.capturedAt,
        processedAt = System.currentTimeMillis(),
        durationMs = durationMs,
        latitude = request.latitude,
        longitude = request.longitude,
        altitude = request.altitude,
        accuracyMeters = request.accuracyMeters,
        userId = request.userId
    )
}
