package com.example.vinculacion.ui.recognition

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinculacion.R
import com.example.vinculacion.data.recognition.RecognitionMatch
import com.example.vinculacion.data.recognition.RecognitionRequest
import com.example.vinculacion.data.recognition.RecognitionResult
import com.example.vinculacion.data.recognition.RecognitionSource
import com.example.vinculacion.data.repository.AuthRepository
import com.example.vinculacion.data.repository.RecognitionRepository
import com.example.vinculacion.data.repository.WeatherRepository
import com.example.vinculacion.data.model.UserLocation
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecognitionViewModel(application: Application) : AndroidViewModel(application) {

    private val recognitionRepository = RecognitionRepository.create(application)
    private val authRepository = AuthRepository(application)
    private val weatherRepository = WeatherRepository(application)
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _uiState = MutableStateFlow(RecognitionUiState())
    val uiState: StateFlow<RecognitionUiState> = _uiState

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onImageSelected(uri: Uri) {
        handleUri(uri, RecognitionSource.IMAGE)
    }

    fun onAudioSelected(uri: Uri) {
        handleUri(uri, RecognitionSource.AUDIO)
    }

    fun onCapturedFile(file: File, source: RecognitionSource, displayName: String? = null) {
        viewModelScope.launch {
            // Capturar ubicación si es una imagen
            var currentLocation: UserLocation? = null
            if (source == RecognitionSource.IMAGE) {
                weatherRepository.getCurrentLocation().fold(
                    onSuccess = { location -> currentLocation = location },
                    onFailure = { /* Continuar sin ubicación */ }
                )
            }
            
            startProcessing(source, displayName ?: file.name, file, currentLocation)
            processFile(FileInfo(file, displayName, source))
        }
    }

    private fun handleUri(uri: Uri, source: RecognitionSource) {
        viewModelScope.launch {
            processUri(uri, source)
        }
    }

    private suspend fun processUri(uri: Uri, source: RecognitionSource) {
        startProcessing(source)

        val context = getApplication<Application>()
        val copyResult = copyUriToStorage(context, uri, source)
        if (copyResult.isFailure) {
            val errorMessage = copyResult.exceptionOrNull()?.localizedMessage
                ?: context.getString(R.string.recognition_error_generic)
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                errorMessage = errorMessage
            )
            _events.send(errorMessage)
            return
        }
        val fileInfo = copyResult.getOrNull() ?: return
        processFile(fileInfo)
    }

    private fun updateStateWithResult(result: RecognitionResult, fileInfo: FileInfo) {
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            isProcessing = false,
            matches = result.matches,
            notes = result.notes,
            savedRecordId = result.savedRecordId,
            selectedFileName = fileInfo.displayName ?: fileInfo.file.name,
            errorMessage = null
        )

        when {
            result.matches.isEmpty() -> {
                result.notes?.let { _events.trySend(it) }
            }
            result.savedRecordId != null -> {
                _events.trySend(context.getString(R.string.recognition_saved_record))
            }
        }
    }

    private fun startProcessing(source: RecognitionSource, fileName: String? = null, capturedImage: File? = null, location: UserLocation? = null) {
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            lastSource = source,
            matches = emptyList(),
            notes = null,
            errorMessage = null,
            savedRecordId = null,
            hasAttempted = true,
            selectedFileName = fileName,
            capturedImagePath = if (source == RecognitionSource.IMAGE) capturedImage?.absolutePath else null,
            currentLocation = location
        )
    }

    private suspend fun processFile(fileInfo: FileInfo) {
        val context = getApplication<Application>()
        val profile = runCatching { authRepository.currentProfile() }.getOrNull()
        val userId = profile?.takeIf { !it.isGuest }?.id

        _uiState.value = _uiState.value.copy(
            selectedFileName = fileInfo.displayName ?: fileInfo.file.name
        )

        val request = RecognitionRequest(
            file = fileInfo.file,
            source = fileInfo.source,
            capturedAt = System.currentTimeMillis(),
            userId = userId
        )

        val result = runCatching { recognitionRepository.recognizeAndStore(request) }
        if (result.isFailure) {
            fileInfo.file.delete()
            val errorMessage = result.exceptionOrNull()?.localizedMessage
                ?: context.getString(R.string.recognition_error_generic)
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                errorMessage = errorMessage,
                selectedFileName = fileInfo.displayName ?: fileInfo.file.name
            )
            _events.send(errorMessage)
            return
        }

        val recognitionResult = result.getOrNull() ?: return
        updateStateWithResult(recognitionResult, fileInfo)
    }

    private suspend fun copyUriToStorage(
        context: Context,
        uri: Uri,
        source: RecognitionSource
    ): Result<FileInfo> = withContext(ioDispatcher) {
        runCatching {
            val resolver = context.contentResolver
            val displayName = resolver.displayName(uri)
            val extension = guessExtension(resolver, uri, source)
            val directory = File(context.filesDir, "recognition_media").apply {
                if (!exists()) mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val fileName = "recognition_${source.name.lowercase(Locale.ROOT)}_$timestamp$extension"
            val destination = File(directory, fileName)
            resolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("No se pudo leer el archivo seleccionado")
            FileInfo(destination, displayName, source)
        }
    }

    private fun guessExtension(resolver: ContentResolver, uri: Uri, source: RecognitionSource): String {
        val mime = resolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
        return when (source) {
            RecognitionSource.IMAGE -> when {
                mime.contains("png") -> ".png"
                mime.contains("webp") -> ".webp"
                mime.contains("jpeg") -> ".jpg"
                mime.contains("jpg") -> ".jpg"
                else -> ".jpg"
            }
            RecognitionSource.AUDIO -> ".wav"
        }
    }

    private fun ContentResolver.displayName(uri: Uri): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return uri.lastPathSegment
        }
        return query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    }

    data class FileInfo(val file: File, val displayName: String?, val source: RecognitionSource)
}

data class RecognitionUiState(
    val isProcessing: Boolean = false,
    val lastSource: RecognitionSource? = null,
    val selectedFileName: String? = null,
    val matches: List<RecognitionMatch> = emptyList(),
    val notes: String? = null,
    val errorMessage: String? = null,
    val savedRecordId: Long? = null,
    val hasAttempted: Boolean = false,
    val capturedImagePath: String? = null,
    val currentLocation: UserLocation? = null
) {
    fun getLocationDescription(): String? {
        return currentLocation?.let { location ->
            "Ubicación: ${location.getLocationDescription()}"
        }
    }
}
