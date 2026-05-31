package com.example.data

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

@JsonClass(generateAdapter = true)
data class RuntimeConfig(
    val version: Int = 1,
    val providers: ProvidersConfig = ProvidersConfig(),
    val mcp: McpSystemConfig = McpSystemConfig(),
    val recording: RecordingConfig = RecordingConfig(),
    val attachments: AttachmentsConfig = AttachmentsConfig()
)

@JsonClass(generateAdapter = true)
data class ProvidersConfig(
    val defaultProviderId: String = "",
    val items: List<ProviderItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ProviderItem(
    val id: String = "",
    val type: String = "",
    val enabled: Boolean = true,
    val endpoint: String = "",
    val apiKeyAlias: String = "",
    val models: ModelConfig = ModelConfig(),
    val capabilities: CapabilityConfig = CapabilityConfig()
)

@JsonClass(generateAdapter = true)
data class ModelConfig(
    val chat: String = "",
    val sttPrimary: String = "",
    val sttFallback: String = "",
    val transcriptPolish: String = ""
)

@JsonClass(generateAdapter = true)
data class CapabilityConfig(
    val text: Boolean = true,
    val imageInline: Boolean = false,
    val pdfInline: Boolean = false,
    val audioInline: Boolean = false,
    val toolCalling: Boolean = false,
    val maxPayloadBytes: Long = 10_000_000
)

@JsonClass(generateAdapter = true)
data class McpSystemConfig(
    val enabled: Boolean = false,
    val servers: List<McpServerConfig> = emptyList()
)

@JsonClass(generateAdapter = true)
data class McpServerConfig(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = true,
    val endpoint: String = "",
    val tokenAlias: String = "",
    val duplicateToolNamePolicy: String = "reject",
    val allowAutoPrefix: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RecordingConfig(
    val meeting: MeetingRecordingConfig = MeetingRecordingConfig()
)

@JsonClass(generateAdapter = true)
data class MeetingRecordingConfig(
    val useForegroundService: Boolean = true,
    val chunkDurationMs: Long = 60_000,
    val sampleRate: Int = 16000,
    val encoding: String = "pcm16"
)

@JsonClass(generateAdapter = true)
data class AttachmentsConfig(
    val maxFileBytes: Long = 10_000_000,
    val unsupportedPolicy: String = "reject_with_reason"
)

class RuntimeConfigRepository(private val context: Context) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(RuntimeConfig::class.java)

    fun loadConfig(): RuntimeConfig {
        val customFile = File(context.getExternalFilesDir("config"), "config.json")
        if (!customFile.exists()) {
            throw ConfigIssueException("ConfigMissing: config.json file not found")
        }
        
        return try {
            adapter.fromJson(customFile.readText()) ?: throw ConfigIssueException("ConfigMissing: Parsed config is null")
        } catch (e: Exception) {
            if (e is ConfigIssueException) throw e
            throw ConfigIssueException("ConfigParseError: ${e.message}")
        }
    }
}
