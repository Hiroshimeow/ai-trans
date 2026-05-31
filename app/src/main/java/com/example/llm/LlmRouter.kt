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
                val apiKey = if (provider.apiKey.isNotEmpty()) {
                    provider.apiKey
                } else if (BuildConfig.DEBUG) {
                    android.util.Log.w("LlmRouter", "WARNING: Falling back to BuildConfig.GEMINI_API_KEY. Config should provide the key.")
                    BuildConfig.GEMINI_API_KEY
                } else ""
                GeminiAdapter(apiKey, mcpRepository)
            }
            ProviderProtocol.OpenAiChatCompletions,
            ProviderProtocol.OllamaChatCompletions,
            ProviderProtocol.CustomHttp -> {
                OpenAiAdapter(provider.endpointUrl, provider.apiKey)
            }
        }
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
                         else throw IllegalStateException("MissingModelConfig: No model configured for provider ${provider.id}")
                         
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
    suspend fun transcribeAudio(audioBytes: ByteArray, promptText: String, provider: LlmProvider, model: String): String {
        val adapter = getAdapter(provider)
        return adapter.transcribeAudio(audioBytes, promptText, model)
    }

    suspend fun polishAudioAndTxt(audioBytes: ByteArray?, rawSTT: String, provider: LlmProvider, model: String): String {
        val adapter = getAdapter(provider)
        return adapter.polishAudioAndTxt(audioBytes, rawSTT, model)
    }
}
