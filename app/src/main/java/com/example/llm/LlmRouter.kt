package com.example.llm

import android.content.Context
import com.example.BuildConfig
import com.example.data.*
import com.example.mcp.McpRepository

class LlmRouter(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val mcpRepository: McpRepository
) {
    private val attachmentPipeline = AttachmentPipeline(context)

    fun getAdapter(provider: LlmProvider): LlmAdapter {
        return when (provider.protocol) {
            ProviderProtocol.GeminiGenerateContent -> {
                val apiKey = if (provider.apiKey.isNotEmpty()) provider.apiKey else getGeminiApiKey()
                GeminiAdapter(apiKey, mcpRepository)
            }
            ProviderProtocol.OpenAiChatCompletions,
            ProviderProtocol.OllamaChatCompletions,
            ProviderProtocol.CustomHttp -> {
                OpenAiAdapter(provider.endpointUrl, provider.apiKey)
            }
        }
    }

    private fun getGeminiApiKey(): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw java.lang.IllegalStateException("Gemini API Key is missing. Please add it via the Settings or Secrets panel in AI Studio.")
        }
        return apiKey
    }

    suspend fun submitMessage(
        chatHistory: List<MessageEntity>,
        userMessage: MessageEntity,
        systemPrompt: String,
        attachments: List<AttachmentEntity>,
        provider: LlmProvider,
        selectedModel: String? = null
    ): String {
        val processedAttachments = attachmentPipeline.processAttachments(attachments)
        val adapter = getAdapter(provider)
        
        val modelToUse = if (!selectedModel.isNullOrEmpty()) selectedModel 
                         else if (provider.models.isNotEmpty()) provider.models.first() 
                         else "gpt-4o"
                         
        val request = CoreChatRequest(
            chatHistory = chatHistory,
            userMessage = userMessage,
            systemPrompt = systemPrompt,
            attachments = processedAttachments,
            modelToUse = modelToUse,
            maxTokens = provider.maxTokens
        )
        
        return adapter.sendChat(request)
    }

    // Facade to old methods for audio which always route to Gemini for now to preserve functionality
    suspend fun transcribeAudio(audioBytes: ByteArray, promptText: String): String {
        val adapter = GeminiAdapter(getGeminiApiKey(), mcpRepository) // defaults to gemini
        return adapter.transcribeAudio(audioBytes, promptText)
    }

    suspend fun polishAudioAndTxt(audioBytes: ByteArray?, rawSTT: String): String {
        val adapter = GeminiAdapter(getGeminiApiKey(), mcpRepository) // defaults to gemini
        return adapter.polishAudioAndTxt(audioBytes, rawSTT)
    }
}
