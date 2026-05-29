package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import java.util.concurrent.ConcurrentHashMap

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
        settingsManager: SettingsManager
    ): ResolvedRoute {
        // Collect all available providers
        val userProviders = LlmProvider.fromJsonArrayString(settingsManager.providersJson)
        val defaultProviders = LlmProvider.getDefaultProviders()
        
        // Combine them nicely: user defined first, then default if not duplicated by ID
        val combinedProviders = mutableListOf<LlmProvider>()
        userProviders.forEach { up ->
            combinedProviders.add(up)
        }
        // Fill up default ones if missing
        defaultProviders.forEach { dp ->
            if (combinedProviders.none { it.id == dp.id }) {
                combinedProviders.add(dp)
            }
        }

        // To protect privacy and prevent empty credentials, we filter providers
        // with any valid API Key or use local build configuration for default native Gemini
        val validatedProviders = combinedProviders.filter { p ->
            if (p.id == "gemini") {
                // If it is the native gemini, check if user provided custom api key or fallback to BuildConfig
                p.apiKey.isNotBlank() || BuildConfig.GEMINI_API_KEY.isNotBlank()
            } else {
                p.apiKey.isNotBlank()
            }
        }

        if (validatedProviders.isEmpty()) {
            // Fallback to absolute default if no configured keys are loaded yet
            val defaultGeminiObj = defaultProviders.first { it.id == "gemini" }
            return ResolvedRoute(
                provider = defaultGeminiObj,
                selectedModel = if (defaultGeminiObj.models.isNotEmpty()) defaultGeminiObj.models.first() else "gemini-3.5-flash",
                routingLog = "No active API keys found. Defaulting to native Gemini provider."
            )
        }

        val now = System.currentTimeMillis()

        // ----------------- CASE 1: COMBO ROUND ROBIN -----------------
        if (settingsManager.routingStrategyComboRoundRobin) {
            // Distribute across Combo models: each provider's models expanded as independent endpoints
            val comboList = mutableListOf<ComboModel>()
            validatedProviders.forEach { p ->
                val endpointModels = p.models.ifEmpty { listOf("gemini-3.5-flash") }
                endpointModels.forEach { m ->
                    comboList.add(ComboModel(p, m))
                }
            }

            if (comboList.isEmpty()) {
                val defaultGeminiObj = defaultProviders.first { it.id == "gemini" }
                return ResolvedRoute(defaultGeminiObj, "gemini-3.5-flash", "Combo list empty")
            }

            // Filter out combos whose provider is currently in cooldown
            val activeCombos = comboList.filter { !isProviderInCooldown(it.provider.id) }

            if (activeCombos.isEmpty()) {
                // If every provider is down/cooling, force clear most expired cooldown or pick the first available to prevent dead ends
                val firstAnyCombo = comboList.first()
                cooldownMap.remove(firstAnyCombo.provider.id)
                return ResolvedRoute(
                    provider = firstAnyCombo.provider,
                    selectedModel = firstAnyCombo.model,
                    routingLog = "All LLM Providers in combo are currently in Cooldown. Force-bypassing block for ${firstAnyCombo.provider.name} (${firstAnyCombo.model})."
                )
            }

            val comboStickyLimit = settingsManager.routingStrategyComboStickyLimit.coerceAtLeast(1)

            // Let's determine the index in activeCombos
            var selectedCombo = activeCombos.getOrNull(currentComboIndex % activeCombos.size) ?: activeCombos.first()

            if (currentComboCallCount < comboStickyLimit) {
                // Keep calling current combo! (Sticky limit not reached)
                currentComboCallCount++
            } else {
                // Sticky limit exceeded, step to next combo!
                currentComboIndex = (currentComboIndex + 1) % activeCombos.size
                selectedCombo = activeCombos[currentComboIndex]
                currentComboCallCount = 1 // reset to first call of the new combo
            }

            return ResolvedRoute(
                provider = selectedCombo.provider,
                selectedModel = selectedCombo.model,
                routingLog = "Combo Round-Robin Selected: ${selectedCombo.provider.name} | Model: ${selectedCombo.model} (Sticky check: $currentComboCallCount/$comboStickyLimit)"
            )
        }

        // ----------------- CASE 2: STANDARD ACCOUNT ROUND ROBIN -----------------
        if (settingsManager.routingStrategyRoundRobin) {
            // Filter out providers that are currently in cooldown
            val activeProviders = validatedProviders.filter { !isProviderInCooldown(it.id) }

            if (activeProviders.isEmpty()) {
                // Force reset cooldown for the first provider if all are dead of cooldown
                val firstAnyProvider = validatedProviders.first()
                cooldownMap.remove(firstAnyProvider.id)
                val selModel = if (firstAnyProvider.models.isNotEmpty()) firstAnyProvider.models.first() else "gemini-3.5-flash"
                return ResolvedRoute(
                    provider = firstAnyProvider,
                    selectedModel = selModel,
                    routingLog = "All LLM Accounts are currently cooling down. Bypassing cooldown timer for ${firstAnyProvider.name}."
                )
            }

            val routeStickyLimit = settingsManager.routingStrategyStickyLimit.coerceAtLeast(1)

            var selectedProvider = activeProviders.getOrNull(currentProviderIndex % activeProviders.size) ?: activeProviders.first()

            if (currentProviderCallCount < routeStickyLimit) {
                // Sticky limit not reached, keep this provider
                currentProviderCallCount++
            } else {
                // Switch to the next available account
                currentProviderIndex = (currentProviderIndex + 1) % activeProviders.size
                selectedProvider = activeProviders[currentProviderIndex]
                currentProviderCallCount = 1
            }

            val selModel = if (selectedProvider.models.isNotEmpty()) selectedProvider.models.first() else "gemini-3.5-flash"

            return ResolvedRoute(
                provider = selectedProvider,
                selectedModel = selModel,
                routingLog = "Account Round-Robin Selected: ${selectedProvider.name} | Model: $selModel (Calls on this account: $currentProviderCallCount/$routeStickyLimit)"
            )
        }

        // ----------------- CASE 3: STANDARD ACTIVE PROVIDER SELECTION (NO ROBIN) -----------------
        val activeId = settingsManager.activeProviderId
        var targetProvider = validatedProviders.find { it.id == activeId }
            ?: fallbackToAnyActiveProvider(validatedProviders, activeId)

        val selModel = if (targetProvider.models.isNotEmpty()) targetProvider.models.first() else "gemini-3.5-flash"
        
        return ResolvedRoute(
            provider = targetProvider,
            selectedModel = selModel,
            routingLog = "Sticky / Standard Session Route Selected: ${targetProvider.name} | Model: $selModel"
        )
    }

    private fun fallbackToAnyActiveProvider(providers: List<LlmProvider>, currentActiveId: String): LlmProvider {
        // Find if activeId is cooling down or missing, fallback to any provider not in cooldown
        val nonCooling = providers.filter { !isProviderInCooldown(it.id) }
        if (nonCooling.isNotEmpty()) {
            return nonCooling.first()
        }
        return providers.first()
    }
}

data class ResolvedRoute(
    val provider: LlmProvider,
    val selectedModel: String,
    val routingLog: String
)
