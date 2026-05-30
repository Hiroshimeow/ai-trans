package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class WorkspaceLlmBridge(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    private val tag = "WorkspaceLlmBridge"

    private fun getApiKey(): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(tag, "API Key is missing or default placeholder!")
            throw IllegalStateException("Gemini API Key is missing. Please add it via the Settings or Secrets panel in AI Studio.")
        }
        return apiKey
    }

    /**
     * Submit user query to LLM, combining with enabled skills, and active session attachments.
     */
    suspend fun submitMessage(
        chatHistory: List<MessageEntity>,
        userMessage: MessageEntity,
        systemPrompt: String,
        attachments: List<AttachmentEntity>,
        sessionEndpointUrl: String? = null,
        sessionModelName: String? = null,
        sessionApiProvider: String? = null,
        sessionApiKey: String? = null,
        sessionMaxTokens: Int? = null
    ): String = withContext(Dispatchers.IO) {
        val activeProvider = if (!sessionApiProvider.isNullOrEmpty()) sessionApiProvider else settingsManager.activeProviderId

        if (activeProvider != "gemini") {
            val endpointUrl = if (!sessionEndpointUrl.isNullOrEmpty()) sessionEndpointUrl else settingsManager.customEndpointUrl
            val modelName = if (!sessionModelName.isNullOrEmpty()) sessionModelName else settingsManager.customModelName
            val apiKey = if (!sessionApiKey.isNullOrEmpty()) sessionApiKey else settingsManager.customApiKey
            val maxTokens = sessionMaxTokens ?: 4096

            return@withContext submitOpenAiCompatibleMessage(
                chatHistory = chatHistory,
                userMessage = userMessage,
                systemPrompt = systemPrompt,
                attachments = attachments,
                endpointUrl = endpointUrl,
                modelName = modelName,
                apiKey = apiKey,
                maxTokens = maxTokens
            )
        }

        // Default Gemini flow
        val model = if (!sessionModelName.isNullOrEmpty()) sessionModelName else settingsManager.chatModel
        val apiKey = if (!sessionApiKey.isNullOrEmpty()) sessionApiKey else getApiKey()

        val contents = mutableListOf<Content>()

        // 1. Build conversation history
        chatHistory.forEach { msg ->
            if (!msg.isError && !msg.isPending) {
                val partsList = mutableListOf<Part>()
                partsList.add(Part(text = msg.content))
                contents.add(Content(role = msg.role, parts = partsList))
            }
        }

        // 2. Build current user turn parts: prompt text + attachment inline data
        val currentUserParts = mutableListOf<Part>()
        currentUserParts.add(Part(text = userMessage.content))

        attachments.forEach { attach ->
            if (attach.status == "selected") {
                val base64Data = loadAttachmentBase64(attach)
                if (base64Data != null) {
                    if (attach.mimeType.startsWith("image/")) {
                        currentUserParts.add(Part(inlineData = InlineData(mimeType = attach.mimeType, data = base64Data)))
                    } else if (isPdfAttachment(attach)) {
                        currentUserParts.add(Part(inlineData = InlineData(mimeType = "application/pdf", data = base64Data)))
                    } else if (isTextAttachment(attach)) {
                        val textContent = loadAttachmentText(attach)
                        if (textContent != null) {
                            currentUserParts.add(Part(text = "\n[Attached File: ${attach.displayName}]\n$textContent\n[End of File]\n"))
                        }
                    }
                }
            }
        }

        contents.add(Content(role = "user", parts = currentUserParts))

        // 3. System instructions
        val systemInstruction = if (systemPrompt.isNotEmpty()) {
            Content(parts = listOf(Part(text = systemPrompt)))
        } else {
            null
        }

        val request = GenerateContentRequest(
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = GenerationConfig(temperature = 0.4f)
        )

        val response = GeminiRetrofitClient.service.generateContent(model, apiKey, request)
        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (responseText != null) {
            return@withContext responseText
        } else {
            val errMsg = response.error?.message ?: "Received empty response from Gemini API"
            throw IOException("Gemini API Error: $errMsg")
        }
    }

    private suspend fun submitOpenAiCompatibleMessage(
        chatHistory: List<MessageEntity>,
        userMessage: MessageEntity,
        systemPrompt: String,
        attachments: List<AttachmentEntity>,
        endpointUrl: String,
        modelName: String,
        apiKey: String,
        maxTokens: Int
    ): String = withContext(Dispatchers.IO) {
        if (endpointUrl.isBlank()) {
            throw IllegalStateException("API Endpoint URL is not configured. Please fill custom endpoint url in settings.")
        }

        val resolvedEndpoint = if (endpointUrl.endsWith("/chat/completions")) {
            endpointUrl
        } else {
            if (endpointUrl.endsWith("/")) {
                endpointUrl + "chat/completions"
            } else {
                endpointUrl + "/chat/completions"
            }
        }

        val authHeader = "Bearer $apiKey"
        val requestMessages = mutableListOf<OpenAiMessage>()

        // 1. System instruction
        if (systemPrompt.isNotEmpty()) {
            requestMessages.add(
                OpenAiMessage(
                    role = "system",
                    content = listOf(OpenAiContentPart(type = "text", text = systemPrompt))
                )
            )
        }

        // 2. Chat history
        chatHistory.forEach { msg ->
            if (!msg.isError && !msg.isPending) {
                requestMessages.add(
                    OpenAiMessage(
                        role = if (msg.role == "model") "assistant" else msg.role,
                        content = listOf(OpenAiContentPart(type = "text", text = msg.content))
                    )
                )
            }
        }

        // 3. User message parts
        val userContentParts = mutableListOf<OpenAiContentPart>()
        userContentParts.add(OpenAiContentPart(type = "text", text = userMessage.content))

        attachments.forEach { attach ->
            if (attach.status == "selected") {
                val base64Data = loadAttachmentBase64(attach)
                if (base64Data != null) {
                    if (attach.mimeType.startsWith("image/")) {
                        val base64Url = "data:${attach.mimeType};base64,$base64Data"
                        userContentParts.add(
                            OpenAiContentPart(
                                type = "image_url",
                                imageUrl = OpenAiImageUrl(url = base64Url)
                            )
                        )
                    } else if (isPdfAttachment(attach)) {
                        userContentParts.add(
                            OpenAiContentPart(
                                type = "text",
                                text = "\n[Attached PDF: ${attach.displayName} - Note: This provider may not support native PDF ingestion without extraction]\n"
                            )
                        )
                    } else if (isTextAttachment(attach)) {
                        val textContent = loadAttachmentText(attach)
                        if (textContent != null) {
                            userContentParts.add(
                                OpenAiContentPart(
                                    type = "text",
                                    text = "\n[Attached File: ${attach.displayName}]\n$textContent\n[End of File]\n"
                                )
                            )
                        }
                    }
                }
            }
        }

        requestMessages.add(OpenAiMessage(role = "user", content = userContentParts))

        val request = OpenAiChatRequest(
            model = modelName,
            messages = requestMessages,
            temperature = 0.4f,
            maxTokens = maxTokens
        )

        try {
            Log.d(tag, "Submitting to Custom Endpoint: $resolvedEndpoint, Model: ${request.model}")
            val response = OpenAiRetrofitClient.service.chatCompletion(
                url = resolvedEndpoint,
                authHeader = authHeader,
                request = request
            )

            val responseText = response.choices?.firstOrNull()?.message?.content
            if (responseText != null) {
                return@withContext responseText
            } else {
                val errMsg = response.error?.message ?: "Received empty response from OpenAI-compatible endpoint"
                throw IOException("OpenAI compatible API Error: $errMsg")
            }
        } catch (e: Exception) {
            Log.e(tag, "OpenAI Call failed", e)
            throw IOException("Custom endpoint connection failed: ${e.message}")
        }
    }

    /**
     * Audio-to-Text Transcription via Gemini process_audio (REST inlineData).
     * cascade primary STT model -> fallback STT model => return text or throws exception.
     */
    suspend fun transcribeAudio(audioFile: File): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val primaryModel = settingsManager.sttModel
        val fallbackModel = settingsManager.fallbackSttModel
        val promptText = settingsManager.sttPrompt

        if (!audioFile.exists()) {
            throw IOException("Audio file target not found: ${audioFile.absolutePath}")
        }

        val audioBytes = audioFile.readBytes()
        if (audioBytes.isEmpty()) {
            throw IOException("Audio file is empty.")
        }

        val detectedMimeType = if (audioFile.name.endsWith(".mp3", ignoreCase = true)) {
            "audio/mp3"
        } else if (audioFile.name.endsWith(".mp4", ignoreCase = true) || audioFile.name.endsWith(".m4a", ignoreCase = true)) {
            "audio/mp4"
        } else {
            "audio/wav"
        }

        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(inlineData = InlineData(mimeType = detectedMimeType, data = base64Audio)),
                        Part(text = promptText)
                    )
                )
            ),
            generationConfig = GenerationConfig(temperature = 0.1f)
        )

        Log.d(tag, "Attempting STT with primary model: $primaryModel")
        try {
            val response = GeminiRetrofitClient.service.generateContent(primaryModel, apiKey, request)
            val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!result.isNullOrBlank()) {
                return@withContext result.trim()
            }
            Log.w(tag, "Primary STT returned blank or empty. Falling back...")
        } catch (e: Exception) {
            Log.e(tag, "Primary STT model failed with: ${e.message}. Attempting fallback...")
        }

        Log.d(tag, "Attempting STT with fallback model: $fallbackModel")
        try {
            val response = GeminiRetrofitClient.service.generateContent(fallbackModel, apiKey, request)
            val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!result.isNullOrBlank()) {
                return@withContext result.trim()
            }
            throw IOException("Fallback STT model also returned empty transcript.")
        } catch (e: Exception) {
            Log.e(tag, "Fallback STT failed: ${e.message}")
            throw IOException("STT transcription failed on both models. Error: ${e.message}")
        }
    }

    private fun loadAttachmentBase64(attachment: AttachmentEntity): String? {
        return try {
            val uri = Uri.parse(attachment.uri)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load attachment content for Base64", e)
            null
        }
    }

    private fun loadAttachmentText(attachment: AttachmentEntity): String? {
        return try {
            val uri = Uri.parse(attachment.uri)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load attachment text", e)
            null
        }
    }

    private fun isTextAttachment(attach: AttachmentEntity): Boolean {
        val mime = attach.mimeType.lowercase()
        if (mime.startsWith("text/")) return true
        if (mime == "application/json" || mime == "application/xml" || mime == "application/javascript" || mime == "application/x-javascript") return true
        val ext = attach.displayName.substringAfterLast('.', "").lowercase()
        val textExts = listOf("txt", "md", "markdown", "json", "xml", "csv", "kt", "java", "py", "js", "ts", "html", "css", "yaml", "yml", "gradle")
        return ext in textExts
    }

    private fun isPdfAttachment(attach: AttachmentEntity): Boolean {
        if (attach.mimeType.equals("application/pdf", ignoreCase = true)) return true
        return attach.displayName.endsWith(".pdf", ignoreCase = true)
    }

    /**
     * Multimodal LLM transcription refinement combining .wav and raw STT text.
     */
    suspend fun polishAudioAndTxt(audioFile: File, rawSTT: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val polishModel = settingsManager.sttModel.ifBlank { "gemini-2.5-flash" }

        val detectedMimeType = if (audioFile.name.endsWith(".mp3", ignoreCase = true)) {
            "audio/mp3"
        } else if (audioFile.name.endsWith(".mp4", ignoreCase = true) || audioFile.name.endsWith(".m4a", ignoreCase = true)) {
            "audio/mp4"
        } else {
            "audio/wav"
        }

        val promptText = """
Bạn là Trợ lý AI chuyên nghiệp về hiệu đính và tối ưu hóa tài liệu/biên bản cuộc họp.
Dưới đây là một file âm thanh ghi âm cuộc họp, đi kèm với nội dung chuyển ngữ thô (raw STT) được ghi lại theo thời gian thực trên thiết bị.
Hãy kết hợp cả hai nguồn thông tin này để hiệu chỉnh chính tả, sửa các từ sai ngữ nghĩa do âm sắc địa phương hoặc nhiễu tạp âm, thêm dấu câu, chia đoạn và định dạng thành một Biên bản họp hoàn chỉnh đầy đủ, rõ ràng và mượt mà bằng tiếng Việt.

NỘI DUNG CHUYỂN NGỮ THÔ (RAW STT):
$rawSTT
"""

        val parts = mutableListOf<Part>()
        if (audioFile.exists() && audioFile.length() > 0L) {
            try {
                val audioBytes = audioFile.readBytes()
                val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
                parts.add(Part(inlineData = InlineData(mimeType = detectedMimeType, data = base64Audio)))
            } catch (e: Exception) {
                Log.w(tag, "Could not load audio file bytes for polishing multimodal context: ${e.message}")
            }
        }
        parts.add(Part(text = promptText))

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = parts)
            ),
            generationConfig = GenerationConfig(temperature = 0.2f)
        )

        Log.d(tag, "Routing multimodal Polish request to model: $polishModel")
        try {
            val response = GeminiRetrofitClient.service.generateContent(polishModel, apiKey, request)
            val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!result.isNullOrBlank()) {
                return@withContext result.trim()
            }
        } catch (e: Exception) {
            Log.e(tag, "Multimodal polish failed: ${e.message}. Attempting text-only fallback...")
        }

        val textFallbackRequest = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = promptText)))
            ),
            generationConfig = GenerationConfig(temperature = 0.2f)
        )
        val response = GeminiRetrofitClient.service.generateContent(polishModel, apiKey, textFallbackRequest)
        val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (!result.isNullOrBlank()) {
            return@withContext result.trim()
        }
        throw IOException("Polishing failed. No feedback from Gemini service.")
    }
}
