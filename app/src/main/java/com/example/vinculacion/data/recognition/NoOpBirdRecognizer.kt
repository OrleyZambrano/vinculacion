package com.example.vinculacion.data.recognition

/**
 * Implementación de respaldo que no realiza inferencia real pero conserva metadatos.
 */
class NoOpBirdRecognizer : BirdRecognitionEngine {
    override suspend fun recognize(request: RecognitionRequest): RecognitionResult {
        return RecognitionResult(
            requestId = request.id,
            matches = emptyList(),
            metadata = RecognitionMetadata(
                capturedAt = request.capturedAt,
                processedAt = System.currentTimeMillis(),
                durationMs = 0L,
                latitude = request.latitude,
                longitude = request.longitude,
                altitude = request.altitude,
                accuracyMeters = request.accuracyMeters,
                userId = request.userId
            ),
            notes = "Inferencia no disponible para ${request.source}"
        )
    }
}
