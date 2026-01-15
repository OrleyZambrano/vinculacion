package com.example.vinculacion.data.recognition

import android.content.Context

/**
 * Administra los motores de reconocimiento disponibles para imagen y audio.
 */
class BirdRecognitionManager private constructor(
    private val imageEngine: BirdRecognitionEngine?,
    private val audioEngine: BirdRecognitionEngine?,
    private val fallbackEngine: BirdRecognitionEngine
) {

    suspend fun recognize(request: RecognitionRequest): RecognitionResult = try {
        val engine = when (request.source) {
            RecognitionSource.IMAGE -> imageEngine ?: fallbackEngine
            RecognitionSource.AUDIO -> audioEngine ?: fallbackEngine
        }
        engine.recognize(request)
    } catch (ex: Exception) {
        fallbackEngine.recognize(request).copy(notes = ex.localizedMessage)
    }

    companion object {
        fun create(
            context: Context,
            imageModelPath: String = RecognitionModelsConfig.DEFAULT_IMAGE_MODEL_PATH,
            audioModelPath: String = RecognitionModelsConfig.DEFAULT_AUDIO_MODEL_PATH,
            fallback: BirdRecognitionEngine = NoOpBirdRecognizer()
        ): BirdRecognitionManager {
            val imageEngine = runCatching { TFLiteBirdImageRecognizer(context, imageModelPath) }.getOrNull()
            val audioEngine = runCatching { TFLiteBirdAudioRecognizer(context, audioModelPath) }.getOrNull()
            return BirdRecognitionManager(imageEngine, audioEngine, fallback)
        }
    }
}
