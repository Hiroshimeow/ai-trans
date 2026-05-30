package com.example.llm

import com.example.data.AttachmentEntity
import com.example.data.MessageEntity
import com.example.mcp.McpRepository
import kotlinx.coroutines.flow.Flow

data class ProviderCapabilities(
    val text: Boolean = true,
    val imageInline: Boolean = false,
    val pdfInline: Boolean = false,
    val audioInline: Boolean = false,
    val toolCalling: Boolean = false,
    val maxPayloadBytes: Long = 10_000_000
)

enum class AttachmentType {
    IMAGE, PDF, TEXT, AUDIO, UNKNOWN
}

data class ProcessedAttachment(
    val attachment: AttachmentEntity,
    val type: AttachmentType,
    val base64Data: String? = null,
    val textContent: String? = null
)

data class CoreChatRequest(
    val chatHistory: List<MessageEntity>,
    val userMessage: MessageEntity,
    val systemPrompt: String,
    val attachments: List<ProcessedAttachment>,
    val modelToUse: String,
    val maxTokens: Int
)

interface LlmAdapter {
    val capabilities: ProviderCapabilities
    suspend fun sendChat(request: CoreChatRequest): String
    suspend fun transcribeAudio(audioBytes: ByteArray, promptText: String, model: String): String
    suspend fun polishAudioAndTxt(audioBytes: ByteArray?, rawSTT: String, model: String): String
}
