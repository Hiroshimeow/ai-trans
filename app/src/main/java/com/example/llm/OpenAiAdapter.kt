package com.example.llm

import android.util.Log
import com.example.data.OpenAiChatRequest
import com.example.data.OpenAiContentPart
import com.example.data.OpenAiImageUrl
import com.example.data.OpenAiMessage
import com.example.data.OpenAiRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class OpenAiAdapter(
    private val endpointUrl: String,
    private val apiKey: String
) : LlmAdapter {
    private val tag = "OpenAiAdapter"

    override val capabilities = ProviderCapabilities(
        text = true,
        imageInline = true,
        pdfInline = false,
        audioInline = false,
        toolCalling = false // MVP: OAI tools not supported yet due to schema differences
    )

    override suspend fun sendChat(request: CoreChatRequest): String = withContext(Dispatchers.IO) {
        if (endpointUrl.isBlank()) {
            throw IllegalStateException("API Endpoint URL is not configured.")
        }

        val resolvedEndpoint = endpointUrl

        val authHeader = "Bearer \$apiKey"
        val requestMessages = mutableListOf<OpenAiMessage>()

        if (request.systemPrompt.isNotEmpty()) {
            requestMessages.add(
                OpenAiMessage(
                    role = "system",
                    content = listOf(OpenAiContentPart(type = "text", text = request.systemPrompt))
                )
            )
        }

        request.chatHistory.forEach { msg ->
            if (!msg.isError && !msg.isPending) {
                requestMessages.add(
                    OpenAiMessage(
                        role = if (msg.role == "model") "assistant" else msg.role,
                        content = listOf(OpenAiContentPart(type = "text", text = msg.content))
                    )
                )
            }
        }

        val userContentParts = mutableListOf<OpenAiContentPart>()
        userContentParts.add(OpenAiContentPart(type = "text", text = request.userMessage.content))

        request.attachments.forEach { attach ->
            when (attach.type) {
                AttachmentType.IMAGE -> {
                    val base64Url = "data:\${attach.attachment.mimeType};base64,\${attach.base64Data}"
                    userContentParts.add(OpenAiContentPart(type = "image_url", imageUrl = OpenAiImageUrl(url = base64Url)))
                }
                AttachmentType.PDF -> {
                    userContentParts.add(OpenAiContentPart(type = "text", text = "\n[Attached PDF: \${attach.attachment.displayName} - Not supported by this provider via inline]\n"))
                }
                AttachmentType.TEXT -> {
                    userContentParts.add(OpenAiContentPart(type = "text", text = "\n[Attached File: \${attach.attachment.displayName}]\n\${attach.textContent}\n[End of File]\n"))
                }
                else -> {}
            }
        }

        requestMessages.add(OpenAiMessage(role = "user", content = userContentParts))

        val oaiRequest = OpenAiChatRequest(
            model = request.modelToUse,
            messages = requestMessages,
            temperature = 0.4f,
            maxTokens = request.maxTokens
        )

        try {
            val response = OpenAiRetrofitClient.service.chatCompletion(
                url = resolvedEndpoint,
                authHeader = authHeader,
                request = oaiRequest
            )
            return@withContext response.choices?.firstOrNull()?.message?.content 
                ?: throw IOException("Empty response from OpenAI-compatible endpoint")
        } catch (e: Exception) {
            Log.e(tag, "OpenAI Call failed", e)
            throw IOException("Custom endpoint connection failed: \${e.message}")
        }
    }

    override suspend fun transcribeAudio(audioBytes: ByteArray, promptText: String, model: String): String {
        throw UnsupportedOperationException("OpenAI Adapter currently does not support audio transcription locally.")
    }

    override suspend fun polishAudioAndTxt(audioBytes: ByteArray?, rawSTT: String, model: String): String {
        throw UnsupportedOperationException("OpenAI Adapter currently does not support multimodal audio polishing locally.")
    }
}
