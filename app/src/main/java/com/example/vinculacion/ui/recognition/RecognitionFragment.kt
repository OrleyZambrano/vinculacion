package com.example.vinculacion.ui.recognition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vinculacion.R
import com.example.vinculacion.data.recognition.RecognitionSource
import com.example.vinculacion.databinding.FragmentRecognitionBinding
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecognitionFragment : Fragment() {

    private var _binding: FragmentRecognitionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecognitionViewModel by viewModels()

    private val matchesAdapter = RecognitionMatchesAdapter()

    private var pendingPermissionAction: PendingPermissionAction = PendingPermissionAction.NONE
    private var pendingImageFile: File? = null
    private var pendingImageUri: Uri? = null
    private var audioRecord: AudioRecord? = null
    private var audioRecordingJob: Job? = null
    private var audioTickerJob: Job? = null
    private var audioRawFile: File? = null
    private var recordingStartMillis: Long = 0L
    private var isRecordingAudio = false

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onImageSelected(it) }
    }

    private val audioPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onAudioSelected(it) }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingPermissionAction
        pendingPermissionAction = PendingPermissionAction.NONE
        if (granted && action == PendingPermissionAction.CAPTURE_IMAGE) {
            launchCameraCapture()
        } else if (!granted && action == PendingPermissionAction.CAPTURE_IMAGE) {
            showMessage(getString(R.string.recognition_error_camera_permission))
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingPermissionAction
        pendingPermissionAction = PendingPermissionAction.NONE
        if (granted && action == PendingPermissionAction.RECORD_AUDIO) {
            startAudioRecording()
        } else if (!granted && action == PendingPermissionAction.RECORD_AUDIO) {
            showMessage(getString(R.string.recognition_error_audio_permission))
        }
    }

    private val captureImageLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingImageFile
        if (success && file != null) {
            viewModel.onCapturedFile(file, RecognitionSource.IMAGE)
        } else {
            file?.delete()
        }
        pendingImageFile = null
        pendingImageUri = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecognitionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupActions()
        collectUiState()
        collectEvents()
    }

    private fun setupRecyclerView() {
        binding.recognitionMatchesList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = matchesAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupActions() {
        binding.pickImageCard.setOnClickListener {
            handleImageCaptureRequest()
        }
        binding.pickAudioCard.setOnClickListener {
            toggleAudioRecording()
        }
        binding.selectImageButton.setOnClickListener {
            imagePicker.launch("image/*")
        }
        binding.selectAudioButton.setOnClickListener {
            audioPicker.launch("audio/*")
        }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { message ->
                    if (message.isNotBlank()) {
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun renderState(state: RecognitionUiState) {
        updateActionState(state)

        val fileName = state.selectedFileName
        binding.selectedFileLabel.isVisible = !fileName.isNullOrBlank()
        if (!fileName.isNullOrBlank()) {
            binding.selectedFileLabel.text = getString(R.string.recognition_selected_file, fileName)
        }

        // Mostrar imagen capturada
        val imagePath = state.capturedImagePath
        binding.capturedImageCard.isVisible = !imagePath.isNullOrBlank()
        if (!imagePath.isNullOrBlank()) {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            bitmap?.let { binding.capturedImageView.setImageBitmap(it) }
        }

        val notes = state.notes
        val locationInfo = state.getLocationDescription()
        
        // Mostrar notas y ubicación de manera integrada
        val hasContent = !notes.isNullOrBlank() || !locationInfo.isNullOrBlank()
        binding.recognitionNotes.isVisible = hasContent
        
        if (hasContent) {
            val fullNotes = buildString {
                if (!notes.isNullOrBlank()) {
                    append(getString(R.string.recognition_notes, notes))
                }
                if (!locationInfo.isNullOrBlank()) {
                    if (!notes.isNullOrBlank()) {
                        append("\n\n")
                    }
                    append(locationInfo)
                }
            }
            binding.recognitionNotes.text = fullNotes
        }

        val errorMessage = state.errorMessage
        binding.recognitionError.isVisible = !errorMessage.isNullOrBlank()
        binding.recognitionError.text = errorMessage

        matchesAdapter.submitList(state.matches)
        binding.recognitionMatchesList.isVisible = state.matches.isNotEmpty()
        binding.recognitionEmptyState.isVisible = state.hasAttempted && state.matches.isEmpty() && errorMessage.isNullOrBlank() && !state.isProcessing
    }

    override fun onDestroyView() {
        stopAudioRecording(processForRecognition = false)
        pendingImageFile = null
        pendingImageUri = null
        _binding = null
        super.onDestroyView()
    }

    private fun handleImageCaptureRequest() {
        if (isRecordingAudio) {
            stopAudioRecording(processForRecognition = false)
        }
        val hasPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchCameraCapture()
        } else {
            pendingPermissionAction = PendingPermissionAction.CAPTURE_IMAGE
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraCapture() {
        val context = requireContext()
        val directory = recognitionMediaDir()
        val file = runCatching { File.createTempFile("capture_image_", ".jpg", directory) }
            .getOrElse {
                showMessage(getString(R.string.recognition_error_generic))
                return
            }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse {
            file.delete()
            showMessage(getString(R.string.recognition_error_generic))
            return
        }
        pendingImageFile = file
        pendingImageUri = uri
        captureImageLauncher.launch(uri)
    }

    private fun toggleAudioRecording() {
        if (isRecordingAudio) {
            stopAudioRecording(processForRecognition = true)
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            startAudioRecording()
        } else {
            pendingPermissionAction = PendingPermissionAction.RECORD_AUDIO
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startAudioRecording() {
        if (isRecordingAudio) return
        val directory = recognitionMediaDir()
        val rawFile = runCatching { File.createTempFile("capture_audio_", ".pcm", directory) }
            .getOrElse {
                showMessage(getString(R.string.recognition_error_audio_recording))
                return
            }
        val bufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            rawFile.delete()
            showMessage(getString(R.string.recognition_error_audio_recording))
            return
        }
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_AUDIO_FORMAT, bufferSize)
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            rawFile.delete()
            showMessage(getString(R.string.recognition_error_audio_recording))
            return
        }
        try {
            recorder.startRecording()
        } catch (ex: IllegalStateException) {
            recorder.release()
            rawFile.delete()
            showMessage(getString(R.string.recognition_error_audio_recording))
            return
        }
        audioRecord = recorder
        audioRawFile = rawFile
        isRecordingAudio = true
        recordingStartMillis = System.currentTimeMillis()
        updateRecordingUi()
        audioRecordingJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            FileOutputStream(rawFile).use { stream ->
                val buffer = ByteArray(bufferSize)
                while (isActive && isRecordingAudio) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                    if (read > 0) {
                        stream.write(buffer, 0, read)
                    }
                }
            }
        }
        audioTickerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && isRecordingAudio) {
                val elapsed = System.currentTimeMillis() - recordingStartMillis
                if (elapsed >= MAX_RECORDING_DURATION_MS) {
                    stopAudioRecording(processForRecognition = true)
                    break
                }
                updateRecordingUi()
                delay(500)
            }
        }
    }

    private fun stopAudioRecording(processForRecognition: Boolean) {
        if (!isRecordingAudio) {
            return
        }
        isRecordingAudio = false
        audioTickerJob?.cancel()
        audioTickerJob = null
        audioRecordingJob?.cancel()
        audioRecordingJob = null
        val recorder = audioRecord
        if (recorder != null) {
            runCatching { recorder.stop() }
            recorder.release()
        }
        audioRecord = null
        updateRecordingUi()
        val rawFile = audioRawFile
        audioRawFile = null
        if (!processForRecognition || rawFile == null || !rawFile.exists() || rawFile.length() == 0L) {
            rawFile?.delete()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val wavFile = withContext(Dispatchers.IO) {
                convertRawToWav(rawFile)
            }
            rawFile.delete()
            if (wavFile != null) {
                viewModel.onCapturedFile(wavFile, RecognitionSource.AUDIO)
            } else {
                showMessage(getString(R.string.recognition_error_audio_recording))
            }
        }
    }

    private fun convertRawToWav(rawFile: File): File? {
        val pcmData = runCatching { rawFile.readBytes() }.getOrElse { return null }
        val wavFile = File(recognitionMediaDir(), "capture_audio_${System.currentTimeMillis()}.wav")
        return runCatching {
            FileOutputStream(wavFile).use { out ->
                val totalAudioLen = pcmData.size
                val totalDataLen = totalAudioLen + 36
                val byteRate = AUDIO_SAMPLE_RATE * AUDIO_BYTES_PER_SAMPLE * AUDIO_CHANNEL_COUNT
                out.write(buildWavHeader(totalAudioLen, totalDataLen, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT, byteRate))
                out.write(pcmData)
            }
            wavFile
        }.getOrElse {
            wavFile.delete()
            null
        }
    }

    private fun buildWavHeader(
        audioLen: Int,
        dataLen: Int,
        sampleRate: Int,
        channels: Int,
        byteRate: Int
    ): ByteArray {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (dataLen and 0xff).toByte()
        header[5] = (dataLen shr 8 and 0xff).toByte()
        header[6] = (dataLen shr 16 and 0xff).toByte()
        header[7] = (dataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * AUDIO_BYTES_PER_SAMPLE).toByte()
        header[33] = 0
        header[34] = (AUDIO_BYTES_PER_SAMPLE * 8).toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (audioLen and 0xff).toByte()
        header[41] = (audioLen shr 8 and 0xff).toByte()
        header[42] = (audioLen shr 16 and 0xff).toByte()
        header[43] = (audioLen shr 24 and 0xff).toByte()
        return header
    }

    private fun updateRecordingUi() {
        binding.pickAudioLabel.text = if (isRecordingAudio) {
            getString(R.string.recognition_stop_recording)
        } else {
            getString(R.string.recognition_capture_audio)
        }
        binding.pickAudioHint.isVisible = !isRecordingAudio
        binding.recordingStatusLabel.isVisible = isRecordingAudio
        if (isRecordingAudio) {
            val elapsed = ((System.currentTimeMillis() - recordingStartMillis) / 1000).toInt()
            val remaining = (MAX_RECORDING_DURATION_SECONDS - elapsed).coerceAtLeast(0)
            binding.recordingStatusLabel.text = getString(R.string.recognition_recording_status, remaining)
        }
        updateActionState(viewModel.uiState.value)
    }

    private fun updateActionState(state: RecognitionUiState) {
        val isProcessing = state.isProcessing
        binding.pickImageCard.isEnabled = !isProcessing && !isRecordingAudio
        binding.selectImageButton.isEnabled = !isProcessing && !isRecordingAudio
        binding.selectAudioButton.isEnabled = !isProcessing && !isRecordingAudio
        binding.pickAudioCard.isEnabled = if (isRecordingAudio) true else !isProcessing
        binding.recognitionProgress.isVisible = isProcessing
    }

    private fun recognitionMediaDir(): File {
        return File(requireContext().filesDir, "recognition_media").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private enum class PendingPermissionAction { NONE, CAPTURE_IMAGE, RECORD_AUDIO }

    companion object {
        private const val AUDIO_SAMPLE_RATE = 16000
        private const val AUDIO_CHANNEL_COUNT = 1
        private const val AUDIO_BYTES_PER_SAMPLE = 2
        private const val AUDIO_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_RECORDING_DURATION_SECONDS = 15
        private const val MAX_RECORDING_DURATION_MS = MAX_RECORDING_DURATION_SECONDS * 1000L

        fun newInstance() = RecognitionFragment()
    }
}
