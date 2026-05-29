package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.*
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "MainViewModel"
    private val context = application.applicationContext

    // Dependencies
    val settingsManager = SettingsManager(context)
    private val database = AppDatabase.getDatabase(context)
    private val bridge = WorkspaceLlmBridge(context, settingsManager)
    private val repository = WorkspaceRepository(context, database, bridge)

    private val recorderHelper = AudioRecorderHelper(context, viewModelScope)
    private val playerHelper = AudioPlayerHelper()
    private val ttsHelper = TtsHelper(context)
    private var localSpeechHelper: OnDeviceSpeechHelper? = null

    // Live Local Speech State
    val liveSpeechResult = MutableStateFlow("")

    // --- State Observables ---
    val allSessions: StateFlow<List<SessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSkills: StateFlow<List<PromptSkillEntity>> = repository.allSkills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecordings: StateFlow<List<RecordingEntity>> = repository.allRecordings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val activeSessionMessages: StateFlow<List<MessageEntity>> = _activeSessionId
        .flatMapLatest { id ->
            if (id != null) repository.getMessagesForSession(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val activeSessionAttachments: StateFlow<List<AttachmentEntity>> = _activeSessionId
        .flatMapLatest { id ->
            if (id != null) repository.getAttachmentsForSession(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Compose Field
    var composerText = MutableStateFlow("")

    // Visual Controls
    val isFloatViewMode = MutableStateFlow(false)
    val floatOcrResult = MutableStateFlow<String?>(null)
    val errorString = MutableStateFlow<String?>(null)

    // Recording Runtime States
    val isRecordingAudio = MutableStateFlow(false)
    val recordingDurationMs = MutableStateFlow(0L)
    val recordingRmsAmplitude = MutableStateFlow(0f)
    val isTranscribingAudio = MutableStateFlow(false)
    val recordAutoStoppedDueToSilence = MutableStateFlow(false)

    // Audio Playback State
    val activeAudioPlaybackPath = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            // Automatically initialize predefined prompt skills if DB table is empty
            repository.initDefaultSkillsIfNeeded()

            // Pre-select the first session if existing, otherwise create one automatically on startup
            allSessions.collect { sessions ->
                if (_activeSessionId.value == null) {
                    if (sessions.isNotEmpty()) {
                        _activeSessionId.value = sessions.first().id
                    } else {
                        val firstId = repository.createNewSession("Daily Workspace Session")
                        _activeSessionId.value = firstId
                    }
                }
            }
        }

        // Configure recorder listeners
        recorderHelper.onAutoStoppedListener = { wavFile ->
            viewModelScope.launch {
                handleCompletedRecording(wavFile)
            }
        }
        recorderHelper.onErrorListener = { errorMsg ->
            isRecordingAudio.value = false
            errorString.value = errorMsg
        }

        playerHelper.onPlaybackCompleteListener = {
            activeAudioPlaybackPath.value = null
        }
    }

    // --- UI Control Triggers ---

    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    fun createSession(title: String) {
        viewModelScope.launch {
            val trimmedTitle = title.trim().ifEmpty { "New Workspace Session" }
            val newId = repository.createNewSession(trimmedTitle)
            _activeSessionId.value = newId
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = allSessions.value.firstOrNull()?.id
            }
        }
    }

    fun sendMessage() {
        val currentText = composerText.value.trim()
        val sessionId = _activeSessionId.value

        if (sessionId == null) {
            errorString.value = "Please select or create a Session first."
            return
        }

        if (currentText.isEmpty()) {
            return
        }

        viewModelScope.launch {
            try {
                // Clear text field
                composerText.value = ""

                // Assemble selected prompt skills into combined system prompt string
                val activeSkills = allSkills.value.filter { it.selected }
                val systemPrompt = if (activeSkills.isNotEmpty()) {
                    activeSkills.joinToString("\n\n") { "--- ${it.title} ---\n${it.content}" }
                } else {
                    ""
                }

                // Send user request
                repository.sendUserMessage(sessionId, currentText, systemPrompt)
            } catch (e: Exception) {
                errorString.value = e.message ?: "Failed to transmit message."
            }
        }
    }

    // --- Skill Selection ---

    fun toggleSkill(skillId: String, selected: Boolean) {
        viewModelScope.launch {
            repository.toggleSkillSelection(skillId, selected)
        }
    }

    fun addNewSkill(title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            repository.addCustomSkill(title.trim(), content.trim())
        }
    }

    fun deleteSkill(skillId: String) {
        viewModelScope.launch {
            repository.deleteSkill(skillId)
        }
    }

    fun restoreBuiltInSkills() {
        viewModelScope.launch {
            repository.restoreBuiltInSkills()
        }
    }

    // --- Attachments Actions ---

    fun addAttachmentFromUri(uri: Uri, displayName: String, mimeType: String, sizeBytes: Long) {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            repository.addAttachment(
                sessionId = sessionId,
                uri = uri.toString(),
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                source = "import"
            )
        }
    }

    fun toggleAttachmentSelection(attachmentId: String, isSelected: Boolean) {
        viewModelScope.launch {
            repository.toggleAttachmentSelection(attachmentId, isSelected)
        }
    }

    fun removeAttachment(attachmentId: String) {
        viewModelScope.launch {
            repository.deleteAttachment(attachmentId)
        }
    }

    // --- Voice Recording Logic ---

    fun toggleAudioRecording() {
        if (isRecordingAudio.value) {
            stopAudioRecording()
        } else {
            startAudioRecording()
        }
    }

    private fun startAudioRecording() {
        // Disables active playbacks
        playerHelper.stopAudio()
        activeAudioPlaybackPath.value = null

        // Apply updated thresholds
        recorderHelper.isSilenceDetectionEnabled = settingsManager.autoStopSilenceEnabled
        recorderHelper.silenceThreshold = settingsManager.silenceThreshold
        recorderHelper.maxSilenceSeconds = settingsManager.maxSilenceSeconds

        recordAutoStoppedDueToSilence.value = false
        val fileRecorded = recorderHelper.startRecording()
        if (fileRecorded != null) {
            isRecordingAudio.value = true
            liveSpeechResult.value = ""

            // Configure local Speech Recognition if configured
            if (settingsManager.useOnDeviceRecognizer) {
                localSpeechHelper = OnDeviceSpeechHelper(context,
                    onResult = { result ->
                        liveSpeechResult.value = result
                        Log.d(tag, "Local continuous transcript update: $result")
                    },
                    onError = { err ->
                        Log.e(tag, "Local SpeechRecognizer error: $err")
                    }
                )
                localSpeechHelper?.startListening(settingsManager.speechLanguage)
            }

            // Launch polling job for visualizer progress inside views
            viewModelScope.launch {
                while (isRecordingAudio.value && recorderHelper.isRecording) {
                    recordingDurationMs.value = recorderHelper.durationMs
                    recordingRmsAmplitude.value = recorderHelper.currentRms

                    // Check if helper auto-stopped itself due to voice-silence duration
                    if (!recorderHelper.isRecording) {
                        recordAutoStoppedDueToSilence.value = true
                        isRecordingAudio.value = false
                        break
                    }
                    delay(100)
                }
            }
        }
    }

    private fun stopAudioRecording() {
        isRecordingAudio.value = false
        recorderHelper.stopRecording()
        localSpeechHelper?.stopListening()
        localSpeechHelper = null
    }

    private suspend fun handleCompletedRecording(wavFile: File) {
        isTranscribingAudio.value = true
        try {
            val durationSecs = wavFile.length() / (16000.0 * 2.0) // Estimate for WAV 16kHz Mono PCM16
            Log.d(tag, "Recording finished -> File: ${wavFile.name}, calculated duration: $durationSecs s")

            val useLocal = settingsManager.useOnDeviceRecognizer
            val localText = liveSpeechResult.value

            val transcript = if (useLocal && localText.isNotBlank()) {
                Log.d(tag, "Applying local on-device transcript: $localText")
                localText
            } else {
                repository.transcribeAudioFile(wavFile)
            }
            Log.d(tag, "Audio STT completed -> Transcript: $transcript")

            // Persist recording to the history panel
            repository.saveCompletedRecording(wavFile, durationSecs, transcript)

            if (transcript.isNotBlank()) {
                // If auto-transcribe-insert is active
                if (settingsManager.autoTranscribeAfterStop) {
                    val currentText = composerText.value
                    if (currentText.isEmpty()) {
                        composerText.value = transcript
                    } else {
                        composerText.value = "$currentText\n$transcript"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to transcribe recorded audio", e)
            errorString.value = e.message ?: "Failed audio transcription."
        } finally {
            isTranscribingAudio.value = false
        }
    }

    // --- Audio Playback ---

    fun toggleAudioPlayback(recording: RecordingEntity) {
        val path = recording.audioPath
        val file = File(path)
        if (!file.exists()) {
            errorString.value = "Audio file does not exist on disk."
            return
        }

        if (activeAudioPlaybackPath.value == path) {
            playerHelper.stopAudio()
            activeAudioPlaybackPath.value = null
        } else {
            playerHelper.playAudio(file)
            activeAudioPlaybackPath.value = path
        }
    }

    fun deleteRecording(recordingId: String, audioPath: String) {
        val file = File(audioPath)
        if (activeAudioPlaybackPath.value == audioPath) {
            playerHelper.stopAudio()
            activeAudioPlaybackPath.value = null
        }
        viewModelScope.launch {
            repository.deleteRecording(recordingId, file)
        }
    }

    fun insertTranscriptIntoComposer(transcript: String) {
        val currentText = composerText.value
        if (currentText.isEmpty()) {
            composerText.value = transcript
        } else {
            composerText.value = "$currentText $transcript"
        }
    }

    // --- Simulate OCR Screenshot Flow (Float UI Card actions) ---

    fun runSimulatedScreenOcr(simulatedText: String, imageUri: Uri) {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            // Write simulated OCR text as extractedText in Room
            val textToUse = simulatedText.ifEmpty { "Simulated OCR content containing technical reference codes." }
            repository.addAttachment(
                sessionId = sessionId,
                uri = imageUri.toString(),
                displayName = "Screen_Snap_${System.currentTimeMillis()}.png",
                mimeType = "image/png",
                sizeBytes = 204800L,
                source = "screenshot"
            )

            // Update Float OCR results
            floatOcrResult.value = "Recognized Text:\n$textToUse"

            // Append directly into composer for continuing chat
            val currentText = composerText.value
            if (currentText.isEmpty()) {
                composerText.value = "Analyze screenshot containing: \"$textToUse\""
            } else {
                composerText.value = "$currentText\n[OCR Snapshot Context: $textToUse]"
            }
        }
    }

    fun clearOcrResults() {
        floatOcrResult.value = null
    }

    fun dismissError() {
        errorString.value = null
    }

    fun speakMessageWithTts(text: String) {
        ttsHelper.speak(text, settingsManager.ttsLanguage)
    }

    fun stopSpeakingTts() {
        ttsHelper.stop()
    }

    fun updateActiveSessionLlmConfig(endpointUrl: String, modelName: String, provider: String, apiKey: String, maxTokens: Int) {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            repository.updateSessionLlmConfig(sessionId, endpointUrl, modelName, provider, apiKey, maxTokens)
        }
    }

    override fun onCleared() {
        super.onCleared()
        recorderHelper.stopRecording()
        playerHelper.stopAudio()
        ttsHelper.shutdown()
        localSpeechHelper?.stopListening()
    }
}
