package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

class ConfigIssueException(message: String) : Exception(message)

enum class LlmCapability {
    Chat, SttPrimary, SttFallback, TranscriptPolish
}

object LlmRouteSelector {
    private const val TAG = "LlmRouteSelector"

    // Map to track cooldown status of each provider: Provider ID -> Cooldown End Timestamp (epoch millis)
    private val cooldownMap = ConcurrentHashMap<String, Long>()

    // Current pointer state for Account-level Round Robin
    private var currentProviderIndex = 0
    private var currentProviderCallCount = 0

    // Current pointer state for Combo-level Round Robin
    private var currentComboIndex = 0
    private var currentComboCallCount = 0

    // Structure of a Combo Model representation
    data class ComboModel(
        val provider: LlmProvider,
        val model: String
    )

    /**
     * Reports an API error on a provider. Automatically triggers cooldown of 60 seconds
     * for this provider, and resets sticky call counters to force switching.
     */
    fun reportFailure(providerId: String) {
        val cooldownDuration = 60_000L // 60 seconds cooldown to prevent rapid repeated failures
        val endTime = System.currentTimeMillis() + cooldownDuration
        cooldownMap[providerId] = endTime
        
        Log.e(TAG, "Provider '$providerId' reported an API error. Placed in cooldown until $endTime (60s block).")
        
        // Reset call counters to trigger a fresh fallback on the next query
        currentProviderCallCount = 0
        currentComboCallCount = 0
    }

    /**
     * Checks if a provider is currently blocked in cooldown.
     */
    fun isProviderInCooldown(providerId: String): Boolean {
        val endTime = cooldownMap[providerId] ?: return false
        val now = System.currentTimeMillis()
        if (now >= endTime) {
            cooldownMap.remove(providerId) // Cooldown expired, remove it!
            return false
        }
        return true
    }

    /**
     * Clear all current cooldowns if needed (e.g. from UI settings refresh)
     */
    fun clearAllCooldowns() {
        cooldownMap.clear()
        currentProviderCallCount = 0
        currentComboCallCount = 0
    }

    /**
     * Strategy routing selector. Given the settings and list of configurations,
     * returns a ResolvedProvider configuration to execute the prompt.
     * Includes fallback options.
     */
    fun selectRoute(
        context: Context,
        settingsManager: SettingsManager,
        runtimeConfigRepo: RuntimeConfigRepository,
        credentialStore: CredentialStore,
        capability: LlmCapability = LlmCapability.Chat
    ): ResolvedRoute {
        val runtimeConfig = runtimeConfigRepo.loadConfig()

        val activeId = runtimeConfig.providers.defaultProviderId
        if (activeId.isBlank()) {
            throw ConfigIssueException("ProviderNotFound: defaultProviderId is missing in stc.json")
        }

        val pItem = runtimeConfig.providers.items.find { it.id == activeId && it.enabled }
            ?: throw ConfigIssueException("ProviderNotFound: Provider '$activeId' not found or disabled in stc.json")

        val resolvedKey = credentialStore.getSecret(pItem.apiKeyAlias)
        if (resolvedKey.isNullOrBlank()) {
             throw ConfigIssueException("CredentialMissing: Secret not found for alias ${pItem.apiKeyAlias}")
        }

        val selModel = when (capability) {
            LlmCapability.Chat -> pItem.models.chat
            LlmCapability.SttPrimary -> pItem.models.sttPrimary
            LlmCapability.SttFallback -> pItem.models.sttFallback
            LlmCapability.TranscriptPolish -> pItem.models.transcriptPolish
        }

        if (selModel.isBlank()) {
            throw ConfigIssueException("MissingModelConfig: No model configured for provider ${pItem.id} capability $capability")
        }

        if (isProviderInCooldown(pItem.id)) {
            throw ConfigIssueException("ProviderInCooldown: Provider ${pItem.id} is currently in a 60-second cooldown period due to recent failures. No configured fallback allowed.")
        }

        val targetProvider = LlmProvider(
            id = pItem.id,
            name = pItem.type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
            endpointUrl = pItem.endpoint,
            apiKey = resolvedKey, // Clean, resolved key, NO ALIAS LEAK
            models = listOf(selModel),
            maxTokens = 4096,
            protocol = if (pItem.type == "gemini") ProviderProtocol.GeminiGenerateContent else ProviderProtocol.OpenAiChatCompletions
        )

        return ResolvedRoute(
            provider = targetProvider,
            selectedModel = selModel,
            routingLog = "Config-First Route Selected: ${targetProvider.name} | Capability: $capability | Model: $selModel"
        )
    }
}

data class ResolvedRoute(
    val provider: LlmProvider,
    val selectedModel: String,
    val routingLog: String
)
