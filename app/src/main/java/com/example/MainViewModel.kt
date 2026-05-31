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
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "MainViewModel"
    private val context = application.applicationContext

    // Dependencies
    val settingsManager = SettingsManager(context)
    private val database = AppDatabase.getDatabase(context)
    val credentialStore = com.example.data.EncryptedCredentialStore(context)
    private val defaultClient = okhttp3.OkHttpClient.Builder().build()
    private val mcpClient = com.example.mcp.McpClientImpl(defaultClient, credentialStore)
    private val runtimeConfigRepo = com.example.data.RuntimeConfigRepository(context)
    val mcpRepository = com.example.mcp.McpRepository(database.mcpDao(), database.toolCallDao(), mcpClient, credentialStore, runtimeConfigRepo)
    private val llmRouter = com.example.llm.LlmRouter(context, settingsManager, mcpRepository)
    private val repository = WorkspaceRepository(context, database, llmRouter)

    private val controller = com.example.audio.RecordingController.getInstance(context)
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

    val pendingToolCalls: StateFlow<List<ToolCallEntity>> = database.toolCallDao().getPendingToolCallsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun approveToolCall(callId: String) {
        viewModelScope.launch {
            val call = database.toolCallDao().getToolCallById(callId)
            if (call != null && call.status == "pending_approval") {
                database.toolCallDao().updateToolCall(call.copy(status = "executing"))
            }
        }
    }

    fun rejectToolCall(callId: String) {
        viewModelScope.launch {
            val call = database.toolCallDao().getToolCallById(callId)
            if (call != null && call.status == "pending_approval") {
                database.toolCallDao().updateToolCall(call.copy(
                    status = "rejected",
                    resultSummary = "Rejected by user.",
                    errorCode = "ToolApprovalRequired"
                ))
            }
        }
    }

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
    val errorString = MutableStateFlow<String?>(null)

    // Recording Runtime States
    val recordingStatus = MutableStateFlow(RecordingStatus.IDLE)
    val isRecordingAudio = MutableStateFlow(false)
    val recordingDurationMs = MutableStateFlow(0L)
    val recordingRmsAmplitude = MutableStateFlow(0f)
    val isTranscribingAudio = MutableStateFlow(false)
    val recordAutoStoppedDueToSilence = MutableStateFlow(false)
    private val liveSpeechSegments = mutableListOf<String>()
    val liveMeetingTranscript = MutableStateFlow("")
    val polishedTranscript = MutableStateFlow("")
    val isMeetingActive = MutableStateFlow(false)
    val isRecordingVoiceQuestion = MutableStateFlow(false)
    private var activeRecordingSessionId: String? = null
    val isPolishingTranscript = MutableStateFlow(false)

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
                        try {
                            val firstId = repository.createNewSession("Daily Workspace Session")
                            _activeSessionId.value = firstId
                        } catch (e: Exception) {
                            errorString.value = e.message ?: "Failed to create default session"
                        }
                    }
                }
            }
        }

        // Configure recorder listeners
        viewModelScope.launch {
            controller.events.collect { event ->
                when (event) {
                    is com.example.audio.RecordingEvent.AutoStopped -> {
                        if (recordingStatus.value != RecordingStatus.SKIPPED) {
                            handleCompletedRecording(event.file)
                        }
                    }
                    is com.example.audio.RecordingEvent.Error -> {
                        isRecordingAudio.value = false
                        errorString.value = event.message
                    }
                    is com.example.audio.RecordingEvent.SilenceStopped -> {
                        recordAutoStoppedDueToSilence.value = true
                        isRecordingAudio.value = false
                    }
                }
            }
        }
        
        viewModelScope.launch {
            controller.recordingState.collect { state ->
                recordingDurationMs.value = state.durationMs
                recordingRmsAmplitude.value = state.rmsAmplitude
                if (state.state == com.example.audio.RecordingState.IDLE && isRecordingAudio.value && !recordAutoStoppedDueToSilence.value) {
                    isRecordingAudio.value = false
                }
            }
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
        val currentMsgs = activeSessionMessages.value
        val currentId = _activeSessionId.value
        if (currentId != null && currentMsgs.isEmpty()) {
            // Current session is already empty, no need to create a new one. Just keep it.
            return
        }
        viewModelScope.launch {
            val trimmedTitle = title.trim().ifEmpty { "New Workspace Session" }
            try {
                val newId = repository.createNewSession(trimmedTitle)
                _activeSessionId.value = newId
            } catch (e: Exception) {
                errorString.value = e.message ?: "Failed to create session"
            }
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
        
        val currentSession = allSessions.value.find { it.id == sessionId }
        if (currentSession?.apiProvider == "unconfigured") {
            errorString.value = "Runtime config missing. Please fix configuration before chatting."
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
                val activeSkills = allSkills.value.filter { it.selected || it.alwaysOn }
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

    fun toggleSkillAlwaysOn(skillId: String, alwaysOn: Boolean) {
        viewModelScope.launch {
            repository.toggleSkillAlwaysOn(skillId, alwaysOn)
        }
    }

    fun updateSkill(skillId: String, title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            repository.updateSkill(skillId, title.trim(), content.trim())
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
        if (!isRecordingAudio.value && isMeetingActive.value) {
            errorString.value = "Cannot record voice message while meeting is active."
            return
        }
        if (isRecordingAudio.value) {
            stopAudioRecording()
        } else {
            startAudioRecording()
        }
    }

    fun toggleVoiceQuestionRecording() {
        if (!isRecordingAudio.value && isMeetingActive.value) {
            errorString.value = "Cannot ask voice question while meeting is active."
            return
        }
        if (isRecordingAudio.value) {
            stopAudioRecording()
        } else {
            isRecordingVoiceQuestion.value = true
            startAudioRecording()
        }
    }

    fun startMeetingRecording() {
        if (isRecordingAudio.value) {
            errorString.value = "Please stop current recording before starting a meeting."
            return
        }
        isMeetingActive.value = true
        liveMeetingTranscript.value = ""
        playerHelper.stopAudio()
        activeAudioPlaybackPath.value = null
        recordAutoStoppedDueToSilence.value = false
        val currentSessionId = UUID.randomUUID().toString()
        activeRecordingSessionId = currentSessionId
        
        viewModelScope.launch(Dispatchers.IO) {
            repository.createLiveRecordingSession(
                sessionId = currentSessionId,
                title = "Họp ngày ${java.text.SimpleDateFormat("dd/MM HH:mm").format(java.util.Date())}",
                mode = "meeting_record"
            )
            
            withContext(Dispatchers.Main) {
                val serviceIntent = android.content.Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_START
                    putExtra("EXTRA_SESSION_ID", currentSessionId)
                    putExtra("EXTRA_IS_MEETING", true)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                
                recordingStatus.value = RecordingStatus.RECORDING
                isRecordingAudio.value = true
                liveSpeechResult.value = ""
                
                val useOnDevice = settingsManager.useOnDeviceRecognizer || true
                if (useOnDevice) {
                    liveSpeechSegments.clear()
                    localSpeechHelper = OnDeviceSpeechHelper(context,
                        onEvent = { event ->
                            when (event) {
                                is TranscriptEvent.Partial -> {
                                    val combined = (liveSpeechSegments + event.text).joinToString(". ")
                                    liveSpeechResult.value = combined
                                    liveMeetingTranscript.value = combined
                                    saveLiveTranscriptSegment(event.text, false, liveSpeechSegments.size)
                                }
                                is TranscriptEvent.Final -> {
                                    liveSpeechSegments.add(event.text)
                                    val combined = liveSpeechSegments.joinToString(". ")
                                    liveSpeechResult.value = combined
                                    liveMeetingTranscript.value = combined
                                    saveLiveTranscriptSegment(event.text, true, liveSpeechSegments.size - 1)
                                }
                            }
                        },
                        onError = { err ->
                            Log.e(tag, "Local SpeechRecognizer error: $err")
                            if (err.contains("timeout", ignoreCase = true)) {
                                recordingStatus.value = RecordingStatus.TIMEOUT
                            } else {
                                recordingStatus.value = RecordingStatus.ERROR
                            }
                        }
                    )
                    localSpeechHelper?.startListening(settingsManager.speechLanguage)
                }
            }
        }
    }

    fun stopMeetingRecording() {
        recordingStatus.value = RecordingStatus.STOPPING
        isRecordingAudio.value = false
        
        val serviceIntent = android.content.Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        context.startService(serviceIntent)
        
        viewModelScope.launch {
            localSpeechHelper?.stopListening()
            localSpeechHelper = null
            // service will take care of finalization and onAutoStoppedListener takes care of handleCompletedRecording
        }
    }

    fun cancelAudioRecording() {
        recordingStatus.value = RecordingStatus.SKIPPED
        isRecordingAudio.value = false
        isMeetingActive.value = false
        isRecordingVoiceQuestion.value = false
        
        val serviceIntent = android.content.Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_CANCEL
        }
        context.startService(serviceIntent)
        
        viewModelScope.launch {
            localSpeechHelper?.stopListening()
            localSpeechHelper = null
        }
    }

    private fun startAudioRecording() {
        playerHelper.stopAudio()
        activeAudioPlaybackPath.value = null

        val isMeeting = isMeetingActive.value
        val isVoiceQ = isRecordingVoiceQuestion.value
        
        val autoStop = if (isMeeting) false else if (isVoiceQ) true else settingsManager.autoStopSilenceEnabled
        val threshold = settingsManager.silenceThreshold
        val maxSeconds = settingsManager.maxSilenceSeconds

        recordAutoStoppedDueToSilence.value = false
        val currentSessionId = UUID.randomUUID().toString()
        activeRecordingSessionId = currentSessionId
        
        val mode = if (isVoiceQ) "quick_voice_note" else "float_quick_ask"
        viewModelScope.launch(Dispatchers.IO) {
            repository.createLiveRecordingSession(
                sessionId = currentSessionId,
                title = "Ghi âm ngày ${java.text.SimpleDateFormat("dd/MM HH:mm").format(java.util.Date())}",
                mode = mode
            )
            
            withContext(Dispatchers.Main) {
                val serviceIntent = android.content.Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_START
                    putExtra("EXTRA_SESSION_ID", currentSessionId)
                    putExtra("EXTRA_IS_MEETING", isMeeting)
                    putExtra("EXTRA_AUTO_STOP", autoStop)
                    putExtra("EXTRA_THRESHOLD", threshold)
                    putExtra("EXTRA_MAX_SECS", maxSeconds)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                recordingStatus.value = RecordingStatus.RECORDING
                isRecordingAudio.value = true
                liveSpeechResult.value = ""

                liveSpeechSegments.clear()
                val useOnDevice = settingsManager.useOnDeviceRecognizer || isMeeting
                if (useOnDevice) {
                    localSpeechHelper = OnDeviceSpeechHelper(context,
                        onEvent = { event ->
                            when (event) {
                                is TranscriptEvent.Partial -> {
                                    val combined = (liveSpeechSegments + event.text).joinToString(". ")
                                    liveSpeechResult.value = combined
                                    if (isMeetingActive.value) {
                                        liveMeetingTranscript.value = combined
                                    }
                                    saveLiveTranscriptSegment(event.text, false, liveSpeechSegments.size)
                                }
                                is TranscriptEvent.Final -> {
                                    liveSpeechSegments.add(event.text)
                                    val combined = liveSpeechSegments.joinToString(". ")
                                    liveSpeechResult.value = combined
                                    if (isMeetingActive.value) {
                                        liveMeetingTranscript.value = combined
                                    }
                                    saveLiveTranscriptSegment(event.text, true, liveSpeechSegments.size - 1)
                                }
                            }
                        },
                        onError = { err ->
                            Log.e(tag, "Local SpeechRecognizer error: $err")
                            if (err.contains("timeout", ignoreCase = true)) {
                                recordingStatus.value = RecordingStatus.TIMEOUT
                            } else {
                                recordingStatus.value = RecordingStatus.ERROR
                            }
                        }
                    )
                    localSpeechHelper?.startListening(settingsManager.speechLanguage)
                }
            }
        }
    }

    private fun stopAudioRecording() {
        recordingStatus.value = RecordingStatus.STOPPING
        isRecordingAudio.value = false

        val serviceIntent = android.content.Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        context.startService(serviceIntent)
        
        viewModelScope.launch {
            localSpeechHelper?.stopListening()
            localSpeechHelper = null
        }
    }

    private fun saveLiveTranscriptSegment(text: String, isFinal: Boolean, index: Int) {
        val sessionId = activeRecordingSessionId ?: return
        viewModelScope.launch {
            try {
                repository.saveLiveTranscriptSegment(sessionId, text, isFinal, index)
            } catch (e: Exception) {
                Log.e(tag, "Failed to write live transcript to database", e)
            }
        }
    }

    private suspend fun handleCompletedRecording(wavFile: File) {
        val isVoiceQ = isRecordingVoiceQuestion.value
        recordingStatus.value = RecordingStatus.TRANSCRIBING
        isTranscribingAudio.value = true
        val isMeeting = isMeetingActive.value
        try {
            val durationSecs = wavFile.length() / (16000.0 * 2.0)
            Log.d(tag, "Recording completed -> File: ${wavFile.name}, duration: $durationSecs s")

            val useLocal = settingsManager.useOnDeviceRecognizer || isMeeting
            val localText = if (isMeeting) liveMeetingTranscript.value else liveSpeechResult.value

            val transcript = if (useLocal && localText.isNotBlank()) {
                Log.d(tag, "Using local on-device transcript: $localText")
                localText
            } else {
                repository.transcribeAudioFile(wavFile)
            }
            Log.d(tag, "STT completed -> Transcript: $transcript")

            // val txtFile = File(wavFile.parentFile, wavFile.nameWithoutExtension + ".txt")
            // txtFile.writeText(transcript)

            val segments = listOf(TranscriptSegment(transcript, 0L))
            val transcriptData = TranscriptData(
                segments = segments,
                source = if (useLocal && localText.isNotBlank()) "OnDevice" else "Gemini",
                metadata = mapOf(
                    "duration" to durationSecs.toString(),
                    "date" to System.currentTimeMillis().toString(),
                    "isMeeting" to isMeeting.toString(),
                    "isVoiceQuestion" to isVoiceQ.toString()
                )
            )
            val dbPayloadValue = transcriptData.toJson()

            val sessionId = activeRecordingSessionId ?: UUID.randomUUID().toString()
            repository.saveCompletedRecording(sessionId, wavFile, durationSecs, dbPayloadValue, isMeeting = isMeeting, isVoiceQuestion = isVoiceQ)

            if (transcript.isNotBlank()) {
                if (isVoiceQ) {
                    if (settingsManager.voiceQuestionAutoSend) {
                        sendVoiceQuestionMessage(transcript)
                    } else {
                        val currentText = composerText.value
                        if (currentText.isEmpty()) {
                            composerText.value = transcript
                        } else {
                            composerText.value = "$currentText\n$transcript"
                        }
                    }
                } else if (settingsManager.autoTranscribeAfterStop && !isMeeting) {
                    val currentText = composerText.value
                    if (currentText.isEmpty()) {
                        composerText.value = transcript
                    } else {
                        composerText.value = "$currentText\n$transcript"
                    }
                }
            }
            recordingStatus.value = RecordingStatus.COMPLETED
        } catch (e: Exception) {
            Log.e(tag, "Failed to transcribe recorded audio", e)
            recordingStatus.value = RecordingStatus.ERROR
            errorString.value = e.message ?: "Failed audio transcription."
        } finally {
            isTranscribingAudio.value = false
            isMeetingActive.value = false
            isRecordingVoiceQuestion.value = false
        }
    }

    private suspend fun sendVoiceQuestionMessage(transcript: String) {
        val sessionId = _activeSessionId.value ?: return
        
        val currentSession = allSessions.value.find { it.id == sessionId }
        if (currentSession?.apiProvider == "unconfigured") {
            errorString.value = "Runtime config missing. Please fix configuration before asking voice questions."
            return
        }
        
        try {
            val activeSkills = allSkills.value.filter { it.selected || it.alwaysOn }
            val systemPrompt = if (activeSkills.isNotEmpty()) {
                activeSkills.joinToString("\n\n") { "--- ${it.title} ---\n${it.content}" }
            } else {
                ""
            }

            val responseText = repository.sendUserMessage(sessionId, transcript, systemPrompt)
            if (settingsManager.voiceQuestionAutoPlayTts && responseText.isNotBlank()) {
                speakMessageWithTts(responseText)
            }
        } catch (e: Exception) {
            Log.e(tag, "Voice question background send error", e)
            errorString.value = e.message ?: "Failed to transmit voice prompt."
        }
    }

    fun polishTranscript(recording: RecordingEntity) {
        if (isPolishingTranscript.value) return
        isPolishingTranscript.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val wavFile = File(recording.audioPath)
                val segmentsFromDb = database.transcriptSegmentDao().getSegmentsForSessionSync(recording.id)
                val rawSTTFromSegments = segmentsFromDb.sortedBy { it.index }.joinToString("\n") { it.text }
                
                val rawSTT = if (rawSTTFromSegments.isNotBlank()) rawSTTFromSegments else {
                    if (recording.transcript.isNotBlank()) {
                         val parsed = TranscriptData.fromJson(recording.transcript)
                         parsed.segments.joinToString("\n") { it.text }
                    } else ""
                }

                Log.d(tag, "Polishing transcript for recording: ${recording.id}, file: ${wavFile.name}")
                val polishedText = repository.polishAudioAndTxt(wavFile, rawSTT)

                // txtFile.writeText(polishedText)

                val updatedSegments = listOf(TranscriptSegment(polishedText, 0L))
                val updatedData = TranscriptData(
                    segments = updatedSegments,
                    source = "Gemini-Polished",
                    metadata = mapOf(
                        "duration" to recording.durationSeconds.toString(),
                        "date" to recording.createdAt.toString(),
                        "polishedAt" to System.currentTimeMillis().toString()
                    )
                )

                val updatedEntity = recording.copy(
                    transcript = updatedData.toJson()
                )
                database.recordingDao().insertRecording(updatedEntity)

                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Hoàn thành hiệu đính AI thành công!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to polish transcript", e)
                launch(Dispatchers.Main) {
                    errorString.value = "Chuyển đổi/Hiệu đính thất bại: ${e.localizedMessage}"
                }
            } finally {
                isPolishingTranscript.value = false
            }
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
        val parsed = TranscriptData.fromJson(transcript)
        val plainText = parsed.segments.joinToString("\n") { it.text }
        val currentText = composerText.value
        if (currentText.isEmpty()) {
            composerText.value = plainText
        } else {
            composerText.value = "$currentText\n$plainText"
        }
    }

    fun dismissError() {
        errorString.value = null
    }

    fun speakMessageWithTts(text: String) {
        ttsHelper.speak(
            text = text,
            languageCode = settingsManager.ttsLanguage,
            speechRate = settingsManager.ttsSpeechRate,
            pitch = settingsManager.ttsPitch
        )
    }

    fun stopSpeakingTts() {
        ttsHelper.stop()
    }

    fun transcribeAttachedFile(attachment: AttachmentEntity) {
        val uriStr = attachment.uri
        val name = attachment.displayName
        isTranscribingAudio.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriStr)
                val tempFile = File(context.cacheDir, "temp_transcribe_${System.currentTimeMillis()}_${name}")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    throw java.io.IOException("Could not resolve attachment content stream.")
                }
                
                val transcript = repository.transcribeAudioFile(tempFile)
                
                launch(Dispatchers.Main) {
                    if (transcript.isNotBlank()) {
                        val currentText = composerText.value
                        if (currentText.isEmpty()) {
                            composerText.value = transcript
                        } else {
                            composerText.value = "$currentText\n$transcript"
                        }
                        android.widget.Toast.makeText(context, "Transcribed $name successfully!", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Transcription returned blank text.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                
                try {
                    tempFile.delete()
                } catch (e: Exception) {
                    // ignore
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to transcribe attached file $name", e)
                launch(Dispatchers.Main) {
                    errorString.value = "Failed to transcribe $name: ${e.localizedMessage}"
                }
            } finally {
                isTranscribingAudio.value = false
            }
        }
    }

    fun updateActiveSessionLlmConfig(endpointUrl: String, modelName: String, provider: String, apiKey: String, maxTokens: Int) {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            repository.updateSessionLlmConfig(sessionId, endpointUrl, modelName, provider, apiKey, maxTokens)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerHelper.stopAudio()
        ttsHelper.shutdown()
        localSpeechHelper?.stopListening()
    }
}

enum class RecordingStatus {
    IDLE,
    RECORDING,
    STOPPING,
    TRANSCRIBING,
    COMPLETED,
    SKIPPED,
    ERROR,
    TIMEOUT
}

data class TranscriptSegment(
    val text: String,
    val timestampMs: Long
)

data class TranscriptData(
    val segments: List<TranscriptSegment>,
    val source: String,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toJson(): String {
        val root = org.json.JSONObject()
        root.put("source", source)
        val meta = org.json.JSONObject()
        metadata.forEach { (k, v) -> meta.put(k, v) }
        root.put("metadata", meta)
        
        val segsArray = org.json.JSONArray()
        segments.forEach { seg ->
            val segObj = org.json.JSONObject()
            segObj.put("text", seg.text)
            segObj.put("timestampMs", seg.timestampMs)
            segsArray.put(segObj)
        }
        root.put("segments", segsArray)
        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): TranscriptData {
            val trimmed = jsonStr.trim()
            if (!trimmed.startsWith("{")) {
                return TranscriptData(
                    segments = listOf(TranscriptSegment(jsonStr, 0L)),
                    source = "Legacy",
                    metadata = emptyMap()
                )
            }
            try {
                val root = org.json.JSONObject(jsonStr)
                val source = root.optString("source", "Unknown")
                
                val metaMap = mutableMapOf<String, String>()
                val metaObj = root.optJSONObject("metadata")
                metaObj?.let {
                    val keys = it.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        metaMap[key] = it.optString(key)
                    }
                }
                
                val segsList = mutableListOf<TranscriptSegment>()
                val segsArray = root.optJSONArray("segments")
                if (segsArray != null) {
                    for (i in 0 until segsArray.length()) {
                        val obj = segsArray.getJSONObject(i)
                        segsList.add(
                            TranscriptSegment(
                                text = obj.getString("text"),
                                timestampMs = obj.getLong("timestampMs")
                            )
                        )
                    }
                } else {
                    segsList.add(TranscriptSegment(root.optString("text", ""), 0L))
                }
                return TranscriptData(segsList, source, metaMap)
            } catch (e: Exception) {
                return TranscriptData(listOf(TranscriptSegment(jsonStr, 0L)), "Error-Fallback")
            }
        }
    }
}
