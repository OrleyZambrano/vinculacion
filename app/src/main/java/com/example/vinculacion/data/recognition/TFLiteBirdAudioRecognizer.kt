package com.example.vinculacion.data.recognition

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Implementación basada en TensorFlow Lite para reconocimiento por audio.
 * Actualmente soporta archivos WAV PCM de 16 bits.
 */
class TFLiteBirdAudioRecognizer(
    private val context: Context,
    private val modelPath: String = RecognitionModelsConfig.DEFAULT_AUDIO_MODEL_PATH
) : BirdRecognitionEngine {

    private val classifier: AudioClassifier? by lazy {
        try {
            AudioClassifier.createFromFile(context, modelPath)
        } catch (ex: Exception) {
            null
        }
    }

    override suspend fun recognize(request: RecognitionRequest): RecognitionResult = withContext(Dispatchers.IO) {
        require(request.source == RecognitionSource.AUDIO) { "El reconocedor de audio solo acepta solicitudes de tipo AUDIO" }
        val classifier = classifier
            ?: return@withContext emptyResult(request, "Modelo de audio no disponible")

        val start = SystemClock.elapsedRealtime()
        val wavData = readPcm16Bit(request.file)
            ?: return@withContext emptyResult(request, "Formato de audio no soportado (se esperaba PCM WAV 16 bits)")
        val (samples, sampleRate) = wavData
        val requiredFormat = classifier.requiredTensorAudioFormat
        if (sampleRate != requiredFormat.sampleRate) {
            return@withContext emptyResult(request, "La frecuencia de muestreo $sampleRate Hz no coincide con la requerida ${requiredFormat.sampleRate} Hz")
        }
        val tensorAudio = TensorAudio.create(requiredFormat, samples.size)
        tensorAudio.load(samples)
        val results = classifier.classify(tensorAudio)
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
                    source = RecognitionSource.AUDIO
                )
            }
        val duration = SystemClock.elapsedRealtime() - start
        RecognitionResult(
            requestId = request.id,
            matches = matches,
            metadata = buildMetadata(request, duration)
        )
    }

    private fun readPcm16Bit(file: File): Pair<FloatArray, Int>? = try {
        file.inputStream().buffered().use { input ->
            val header = ByteArray(44)
            if (input.read(header) != header.size) return null
            val byteOrder = ByteOrder.LITTLE_ENDIAN
            val sampleRate = ByteBuffer.wrap(header, 24, 4).order(byteOrder).int
            val bitsPerSample = ByteBuffer.wrap(header, 34, 2).order(byteOrder).short.toInt()
            if (bitsPerSample != 16) return null
            val dataSize = ByteBuffer.wrap(header, 40, 4).order(byteOrder).int
            val data = ByteArray(dataSize)
            var bytesRead = 0
            while (bytesRead < dataSize) {
                val read = input.read(data, bytesRead, dataSize - bytesRead)
                if (read <= 0) break
                bytesRead += read
            }
            if (bytesRead == 0) return null
            val shortBuffer = ByteBuffer.wrap(data, 0, bytesRead).order(byteOrder).asShortBuffer()
            val floatArray = FloatArray(shortBuffer.remaining())
            var i = 0
            while (shortBuffer.hasRemaining()) {
                floatArray[i++] = shortBuffer.get() / Short.MAX_VALUE.toFloat()
            }
            floatArray to sampleRate
        }
    } catch (_: Exception) {
        null
    }

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

    private fun emptyResult(request: RecognitionRequest, note: String) = RecognitionResult(
        requestId = request.id,
        matches = emptyList(),
        metadata = buildMetadata(request, durationMs = 0L),
        notes = note
    )
}
