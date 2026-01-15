package com.example.vinculacion.data.recognition

/**
 * Ubicación y configuración de los modelos locales utilizados para reconocimiento de aves.
 */
object RecognitionModelsConfig {
    const val DEFAULT_IMAGE_MODEL_PATH = "models/bird_image_classifier.tflite"
    const val DEFAULT_IMAGE_LABELS_PATH = "models/bird_image_labels.txt"
    const val DEFAULT_AUDIO_MODEL_PATH = "models/bird_audio_classifier.tflite"
    const val DEFAULT_AUDIO_LABELS_PATH = "models/bird_audio_labels.txt"

    const val DEFAULT_TOP_K = 3
    const val DEFAULT_CONFIDENCE_THRESHOLD = 0.35f
}
