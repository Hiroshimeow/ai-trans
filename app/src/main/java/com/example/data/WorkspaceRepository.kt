package com.example.data

import com.example.TranscriptData
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class WorkspaceRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val llmRouter: com.example.llm.LlmRouter
) {
    private val tag = "WorkspaceRepository"
    private val settingsManager = SettingsManager(context)

    val allSessions: Flow<List<SessionEntity>> = database.sessionDao().getAllSessions()
    val allSkills: Flow<List<PromptSkillEntity>> = database.promptSkillDao().getPromptSkillsFlow()
    val allRecordings: Flow<List<RecordingEntity>> = database.recordingDao().getRecordingsFlow()

    suspend fun initDefaultSkillsIfNeeded() = withContext(Dispatchers.IO) {
        val existing = database.promptSkillDao().getPromptSkillsSync()
        if (existing.isEmpty()) {
            restoreBuiltInSkills()
        }
    }

    suspend fun restoreBuiltInSkills() = withContext(Dispatchers.IO) {
        database.promptSkillDao().deleteBuiltInSkills()
        val defaultSkills = listOf(
            PromptSkillEntity(
                id = "skill_reviewer",
                title = "Reviewer mode",
                content = "You are an elite senior reviewer. Review the provided input carefully, point out potential flaws, edge cases, and design issues, and suggest concrete fixes.",
                selected = false,
                builtIn = true,
                createdAt = System.currentTimeMillis()
            ),
            PromptSkillEntity(
                id = "skill_root_cause",
                title = "Root cause thinking",
                content = "Solve problems by drilling down to the root cause. Ask 'Why' multiple times and present deeply reasoned conclusions.",
                selected = false,
                builtIn = true,
                createdAt = System.currentTimeMillis() + 1
            ),
            PromptSkillEntity(
                id = "skill_concise",
                title = "Answer concisely",
                content = "Be extremely concise, direct, and to the point. Omit introductory framing and polite signposts.",
                selected = false,
                builtIn = true,
                createdAt = System.currentTimeMillis() + 2
            ),
            PromptSkillEntity(
                id = "skill_step_by_step",
                title = "Explain step by step",
                content = "Break down your thoughts and explain the solution in logical, step-by-step processes to ensure clarity.",
                selected = false,
                builtIn = true,
                createdAt = System.currentTimeMillis() + 3
            ),
            PromptSkillEntity(
                id = "skill_security",
                title = "Security mindset",
                content = "Analyze the prompt from a security standpoint. Find vulnerabilities, exploit scenarios, and suggest secure defensive patterns.",
                selected = false,
                builtIn = true,
                createdAt = System.currentTimeMillis() + 4
            ),
            PromptSkillEntity(
                id = "skill_translate_vi",
                title = "Translate to Vietnamese",
                content = "Formulate your response and output it fully translated into natural, idiomatic Vietnamese.",
                selected = false,
                builtIn = true,
                createdAt = System.currentTimeMillis() + 5
            ),
            PromptSkillEntity(
                id = "skill_code_cleanup",
                title = "Code cleanup",
                content = "Analyze the code and clean it up. Refactor for readability, eliminate redundant declarations, improve naming, and adhere to clean code principles without changing functionality.",
                selected = false,
                builtIn = true,
                createdAt = System.currentTimeMillis() + 6
            )
        )
        defaultSkills.forEach {
            database.promptSkillDao().insertSkill(it)
        }
    }

    // --- Session Actions ---
    suspend fun createNewSession(title: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        // Snapshot active settings definitions
        val activeId = settingsManager.activeProviderId
        val providers = LlmProvider.fromJsonArrayString(settingsManager.providersJson)
        val activeProvider = providers.find { it.id == activeId } 
            ?: LlmProvider.getDefaultProviders().find { it.id == activeId } 
            ?: LlmProvider.getDefaultProviders().first()

        val endpoint = activeProvider.endpointUrl
        val model = if (activeProvider.models.isNotEmpty()) activeProvider.models.first() else ""
        val provider = activeProvider.id
        val apiKey = activeProvider.apiKey
        val maxTokens = activeProvider.maxTokens

        val session = SessionEntity(
            id = id, 
            title = title, 
            createdAt = now, 
            updatedAt = now,
            endpointUrl = endpoint,
            modelName = model,
            apiProvider = provider,
            apiKey = SecurityHelper.encrypt(apiKey),
            maxTokens = maxTokens
        )
        database.sessionDao().insertSession(session)
        id
    }

    suspend fun updateSessionLlmConfig(
        sessionId: String,
        endpointUrl: String,
        modelName: String,
        provider: String,
        apiKey: String,
        maxTokens: Int
    ) = withContext(Dispatchers.IO) {
        val session = database.sessionDao().getSessionById(sessionId)
        if (session != null) {
            database.sessionDao().updateSession(
                session.copy(
                    endpointUrl = endpointUrl,
                    modelName = modelName,
                    apiProvider = provider,
                    apiKey = SecurityHelper.encrypt(apiKey),
                    maxTokens = maxTokens,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        database.sessionDao().deleteSessionById(sessionId)
        database.sessionDao().deleteMessagesBySessionId(sessionId)
        database.sessionDao().deleteAttachmentsBySessionId(sessionId)
    }

    // --- Messages Flow ---
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>> {
        return database.messageDao().getMessagesForSession(sessionId)
    }

    suspend fun sendUserMessage(
        sessionId: String,
        content: String,
        systemPrompt: String
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val userMsgId = UUID.randomUUID().toString()

        // 1. Fetch currently selected attachments in this session
        val allAttachments = database.attachmentDao().getAttachmentsForSession(sessionId).firstOrNull() ?: emptyList()
        val selectedAttachments = allAttachments.filter { it.status == "selected" }
        val selectedIds = selectedAttachments.map { it.id }

        // 2. Insert User Message
        val userMsg = MessageEntity(
            id = userMsgId,
            sessionId = sessionId,
            role = "user",
            content = content,
            attachmentIdsJson = org.json.JSONArray(selectedIds).toString(),
            systemPromptUsed = systemPrompt,
            createdAt = now
        )
        database.messageDao().insertMessage(userMsg)

        // 3. Mark selected attachments as "already_in_chat" (Unchecked list item)
        selectedAttachments.forEach { attach ->
            database.attachmentDao().updateAttachmentStatus(attach.id, "already_in_chat")
        }

        // 4. Update session timestamp
        val session = database.sessionDao().getSessionById(sessionId)
        if (session != null) {
            database.sessionDao().updateSession(session.copy(updatedAt = now))
        }

        // 5. Append Pending Assistant Message
        val assistantMsgId = UUID.randomUUID().toString()
        val pendingMsg = MessageEntity(
            id = assistantMsgId,
            sessionId = sessionId,
            role = "assistant",
            content = "Thinking...",
            attachmentIdsJson = "[]",
            systemPromptUsed = systemPrompt,
            createdAt = now + 1,
            isPending = true
        )
        database.messageDao().insertMessage(pendingMsg)

        try {
            // 6. Fetch full message history to provide model context
            val history = database.messageDao().getMessagesForSession(sessionId).firstOrNull() ?: emptyList()
            val chatHistory = history.filter { it.id != userMsgId && it.id != assistantMsgId }

            // 7. Fire API call to LLM with Route-selector Retry Loop
            var responseText = ""
            var lastError: Exception? = null
            val maxAttempts = 3
            
            for (attempt in 1..maxAttempts) {
                // Determine route dynamically based on active routing rules & cooldown status
                val route = com.example.data.LlmRouteSelector.selectRoute(context, settingsManager, RuntimeConfigRepository(context), EncryptedCredentialStore(context))
                Log.d(tag, "LLM Route attempt $attempt/$maxAttempts via routing selector: ${route.routingLog}")
                
                try {
                    responseText = llmRouter.submitMessage(
                        chatHistory = chatHistory,
                        userMessage = userMsg,
                        systemPrompt = systemPrompt,
                        attachments = selectedAttachments,
                        provider = route.provider,
                        selectedModel = route.selectedModel
                    )
                    // Success! Log and break from retry loop
                    Log.d(tag, "LLM Route execution succeeded on attempt $attempt with provider: ${route.provider.id}")
                    lastError = null
                    break
                } catch (e: Exception) {
                    Log.e(tag, "LLM Route invocation failed on attempt $attempt with provider ${route.provider.id}: ${e.message}")
                    lastError = e
                    // Place this provider in 60s cooldown to prevent repeated near-instant failures
                    com.example.data.LlmRouteSelector.reportFailure(route.provider.id)
                }
            }

            if (lastError != null) {
                throw lastError
            }

            // 8. Update Assistant message with success response
            database.messageDao().insertMessage(
                pendingMsg.copy(
                    content = responseText,
                    isPending = false,
                    isError = false,
                    createdAt = System.currentTimeMillis()
                )
            )
            return@withContext responseText
        } catch (e: Exception) {
            Log.e(tag, "LLM invocation error after exhausting fallback paths", e)
            // 9. Update Assistant message to error status
            database.messageDao().insertMessage(
                pendingMsg.copy(
                    content = "Error calling LLM APIs: ${e.message}",
                    isPending = false,
                    isError = true,
                    createdAt = System.currentTimeMillis()
                )
            )
            throw e
        }
    }

    // --- Attachment Actions ---
    fun getAttachmentsForSession(sessionId: String): Flow<List<AttachmentEntity>> {
        return database.attachmentDao().getAttachmentsForSession(sessionId)
    }

    suspend fun addAttachment(
        sessionId: String,
        uri: String,
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
        source: String
    ) = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val attachment = AttachmentEntity(
            id = id,
            sessionId = sessionId,
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            source = source,
            status = "selected", // auto-select newly added items for prompt
            extractedText = null,
            thumbnailUri = if (mimeType.startsWith("image/")) uri else null
        )
        database.attachmentDao().insertAttachment(attachment)
    }

    suspend fun toggleAttachmentSelection(id: String, selected: Boolean) = withContext(Dispatchers.IO) {
        val status = if (selected) "selected" else "available"
        database.attachmentDao().updateAttachmentStatus(id, status)
    }

    suspend fun deleteAttachment(id: String) = withContext(Dispatchers.IO) {
        database.attachmentDao().deleteAttachmentById(id)
    }

    // --- Skills Management ---
    suspend fun toggleSkillSelection(id: String, selected: Boolean) = withContext(Dispatchers.IO) {
        database.promptSkillDao().updateSkillSelection(id, selected)
    }

    suspend fun toggleSkillAlwaysOn(id: String, alwaysOn: Boolean) = withContext(Dispatchers.IO) {
        database.promptSkillDao().updateSkillAlwaysOn(id, alwaysOn)
    }

    suspend fun updateSkill(id: String, title: String, content: String) = withContext(Dispatchers.IO) {
        database.promptSkillDao().updateSkillTitleAndContent(id, title, content)
    }

    suspend fun addCustomSkill(title: String, content: String) = withContext(Dispatchers.IO) {
        val skill = PromptSkillEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content,
            selected = false,
            builtIn = false,
            createdAt = System.currentTimeMillis()
        )
        database.promptSkillDao().insertSkill(skill)
    }

    suspend fun deleteSkill(id: String) = withContext(Dispatchers.IO) {
        database.promptSkillDao().deleteSkillById(id)
    }

    // --- Recordings Management ---
    suspend fun createLiveRecordingSession(sessionId: String, title: String, mode: String) = withContext(Dispatchers.IO) {
        val sessionEntity = RecordingSessionEntity(
            id = sessionId,
            workspaceSessionId = null,
            title = title,
            mode = mode,
            audioPath = null,
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            durationMs = 0L,
            status = "recording",
            stopReason = null,
            providerId = "gemini",
            language = "vi",
            finalTranscript = null,
            summary = null,
            actionItemsJson = null,
            errorCode = null,
            errorMessage = null
        )
        database.recordingSessionDao().insertRecordingSession(sessionEntity)
    }

    suspend fun saveLiveTranscriptSegment(sessionId: String, text: String, isFinal: Boolean, index: Int) = withContext(Dispatchers.IO) {
        val segmentId = if (isFinal) UUID.randomUUID().toString() else "${sessionId}_partial"
        val segment = TranscriptSegmentEntity(
            id = segmentId,
            recordingSessionId = sessionId,
            index = index,
            startMs = 0L,
            endMs = null,
            text = text,
            isFinal = isFinal,
            speakerLabel = null,
            language = null,
            confidence = null,
            source = "local_live",
            createdAt = System.currentTimeMillis()
        )
        database.transcriptSegmentDao().insertSegments(listOf(segment))
    }

    suspend fun saveCompletedRecording(sessionId: String, audioFile: File, durationSeconds: Double, transcriptJson: String, isMeeting: Boolean = false, isVoiceQuestion: Boolean = false) = withContext(Dispatchers.IO) {
        val id = sessionId
        val recording = RecordingEntity(
            id = id,
            audioPath = audioFile.absolutePath,
            durationSeconds = durationSeconds,
            transcript = transcriptJson,
            createdAt = System.currentTimeMillis(),
            error = null
        )
        database.recordingDao().insertRecording(recording)

        // Parse raw text for structured recording session insertion
        val plainText = try {
            val parsed = TranscriptData.fromJson(transcriptJson)
            parsed.segments.joinToString("\n") { seg -> seg.text }
        } catch (e: Exception) {
            transcriptJson
        }

        // Populate new RecordingSessionEntity for modern tracking as requested by the architecture verdict
        val mode = when {
            isMeeting -> "meeting_record"
            isVoiceQuestion -> "quick_voice_note"
            else -> "float_quick_ask"
        }
        val sessionEntity = RecordingSessionEntity(
            id = id,
            workspaceSessionId = null,
            title = if (isMeeting) "Họp ngày ${java.text.SimpleDateFormat("dd/MM HH:mm").format(java.util.Date())}" else "Ghi âm ngày ${java.text.SimpleDateFormat("dd/MM HH:mm").format(java.util.Date())}",
            mode = mode,
            audioPath = audioFile.absolutePath,
            startedAt = System.currentTimeMillis() - (durationSeconds * 1000).toLong(),
            endedAt = System.currentTimeMillis(),
            durationMs = (durationSeconds * 1000).toLong(),
            status = "completed",
            stopReason = "manual",
            providerId = "gemini",
            language = "vi",
            finalTranscript = plainText,
            summary = null,
            actionItemsJson = null,
            errorCode = null,
            errorMessage = null
        )
        database.recordingSessionDao().insertRecordingSession(sessionEntity)

        // Populate TranscriptSegmentEntity for raw transcript segment persistence
        val segment = TranscriptSegmentEntity(
            id = UUID.randomUUID().toString(),
            recordingSessionId = id,
            index = 0,
            startMs = 0L,
            endMs = (durationSeconds * 1000).toLong(),
            text = plainText,
            isFinal = true,
            speakerLabel = "Speaker 0",
            language = "vi",
            confidence = 1.0f,
            source = if (isVoiceQuestion) "continuous_speech" else "standalone",
            createdAt = System.currentTimeMillis()
        )
        database.transcriptSegmentDao().insertSegments(listOf(segment))
    }

    suspend fun deleteRecording(id: String, fileToDelete: File?) = withContext(Dispatchers.IO) {
        database.recordingDao().deleteRecordingById(id)
        fileToDelete?.let {
            if (it.exists()) {
                it.delete()
            }
        }
    }

    suspend fun transcribeAudioFile(audioFile: java.io.File): String {
        val audioBytes = audioFile.readBytes()
        val sttPrompt = settingsManager.sttPrompt
        val route = com.example.data.LlmRouteSelector.selectRoute(context, settingsManager, RuntimeConfigRepository(context), EncryptedCredentialStore(context), com.example.data.LlmCapability.SttPrimary)
        return llmRouter.transcribeAudio(audioBytes, sttPrompt, route.provider, route.selectedModel)
    }

    suspend fun polishAudioAndTxt(audioFile: java.io.File, rawSTT: String): String {
        val audioBytes = if (audioFile.exists() && audioFile.length() > 0) audioFile.readBytes() else null
        val route = com.example.data.LlmRouteSelector.selectRoute(context, settingsManager, RuntimeConfigRepository(context), EncryptedCredentialStore(context), com.example.data.LlmCapability.TranscriptPolish)
        return llmRouter.polishAudioAndTxt(audioBytes, rawSTT, route.provider, route.selectedModel)
    }
}
