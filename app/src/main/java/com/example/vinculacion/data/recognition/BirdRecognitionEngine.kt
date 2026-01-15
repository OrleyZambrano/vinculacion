package com.example.vinculacion.data.recognition

/**
 * Contrato básico para ejecutar modelos de reconocimiento de aves.
 */
interface BirdRecognitionEngine {
    suspend fun recognize(request: RecognitionRequest): RecognitionResult
}
