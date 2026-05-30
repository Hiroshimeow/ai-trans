package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("screen_chat_settings", Context.MODE_PRIVATE)

    var chatModel: String
        get() = prefs.getString("chat_model", "") ?: ""
        set(value) = prefs.edit().putString("chat_model", value).apply()

    var sttModel: String
        get() = prefs.getString("stt_model", "") ?: ""
        set(value) = prefs.edit().putString("stt_model", value).apply()

    var fallbackSttModel: String
        get() = prefs.getString("fallback_stt_model", "") ?: ""
        set(value) = prefs.edit().putString("fallback_stt_model", value).apply()

    var vllmBaseUrl: String
        get() = prefs.getString("vllm_base_url", "") ?: ""
        set(value) = prefs.edit().putString("vllm_base_url", value).apply()

    var sttPrompt: String
        get() = prefs.getString("stt_prompt", "Transcribe this audio. Preserve technical terms. Prefer Vietnamese. Return only transcript.") ?: ""
        set(value) = prefs.edit().putString("stt_prompt", value).apply()

    var preprocessEnabled: Boolean
        get() = prefs.getBoolean("preprocess_enabled", true)
        set(value) = prefs.edit().putBoolean("preprocess_enabled", value).apply()

    var vadProvider: String
        get() = prefs.getString("vad_provider", "energy") ?: "energy"
        set(value) = prefs.edit().putString("vad_provider", value).apply()

    var recordMaxMinutes: Int
        get() = prefs.getInt("record_max_minutes", 10)
        set(value) = prefs.edit().putInt("record_max_minutes", value).apply()

    var chunkMinutes: Int
        get() = prefs.getInt("chunk_minutes", 2)
        set(value) = prefs.edit().putInt("chunk_minutes", value).apply()

    var sttTimeoutSeconds: Int
        get() = prefs.getInt("stt_timeout_seconds", 30)
        set(value) = prefs.edit().putInt("stt_timeout_seconds", value).apply()

    var postprocessMode: String
        get() = prefs.getString("postprocess_mode", "none") ?: "none"
        set(value) = prefs.edit().putString("postprocess_mode", value).apply()

    // Silence detection & Auto-stop controls
    var autoStopSilenceEnabled: Boolean
        get() = prefs.getBoolean("auto_stop_silence_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_stop_silence_enabled", value).apply()

    var silenceThreshold: Float
        get() = prefs.getFloat("silence_threshold", 0.015f)
        set(value) = prefs.edit().putFloat("silence_threshold", value).apply()

    var maxSilenceSeconds: Int
        get() = prefs.getInt("max_silence_seconds", 3)
        set(value) = prefs.edit().putInt("max_silence_seconds", value).apply()

    var autoTranscribeAfterStop: Boolean
        get() = prefs.getBoolean("auto_transcribe_after_stop", true)
        set(value) = prefs.edit().putBoolean("auto_transcribe_after_stop", value).apply()

    var customEndpointUrl: String
        get() = prefs.getString("custom_endpoint_url", "") ?: ""
        set(value) = prefs.edit().putString("custom_endpoint_url", value).apply()

    var customModelName: String
        get() = prefs.getString("custom_model_name", "") ?: ""
        set(value) = prefs.edit().putString("custom_model_name", value).apply()

    var customApiKey: String
        get() = SecurityHelper.decrypt(prefs.getString("custom_api_key", ""))
        set(value) = prefs.edit().putString("custom_api_key", SecurityHelper.encrypt(value)).apply()

    var preferredProvider: String
        get() = prefs.getString("preferred_provider", "gemini") ?: "gemini"
        set(value) = prefs.edit().putString("preferred_provider", value).apply()

    var providersJson: String
        get() = prefs.getString("providers_json", "") ?: ""
        set(value) = prefs.edit().putString("providers_json", value).apply()

    var activeProviderId: String
        get() = prefs.getString("active_provider_id", "gemini") ?: "gemini"
        set(value) = prefs.edit().putString("active_provider_id", value).apply()

    var speechLanguage: String
        get() = prefs.getString("speech_language", "vi-VN") ?: "vi-VN"
        set(value) = prefs.edit().putString("speech_language", value).apply()

    var ttsLanguage: String
        get() = prefs.getString("tts_language", "vi") ?: "vi"
        set(value) = prefs.edit().putString("tts_language", value).apply()

    var ttsSpeechRate: Float
        get() = prefs.getFloat("tts_speech_rate", 1.0f)
        set(value) = prefs.edit().putFloat("tts_speech_rate", value).apply()

    var ttsPitch: Float
        get() = prefs.getFloat("tts_pitch", 1.0f)
        set(value) = prefs.edit().putFloat("tts_pitch", value).apply()

    var useOnDeviceRecognizer: Boolean
        get() = prefs.getBoolean("use_on_device_recognizer", false)
        set(value) = prefs.edit().putBoolean("use_on_device_recognizer", value).apply()

    var voiceQuestionAutoSend: Boolean
        get() = prefs.getBoolean("voice_question_auto_send", true)
        set(value) = prefs.edit().putBoolean("voice_question_auto_send", value).apply()

    var voiceQuestionAutoPlayTts: Boolean
        get() = prefs.getBoolean("voice_question_auto_play_tts", false)
        set(value) = prefs.edit().putBoolean("voice_question_auto_play_tts", value).apply()

    var routingStrategyRoundRobin: Boolean
        get() = prefs.getBoolean("routing_strategy_round_robin", false)
        set(value) = prefs.edit().putBoolean("routing_strategy_round_robin", value).apply()

    var routingStrategyStickyLimit: Int
        get() = prefs.getInt("routing_strategy_sticky_limit", 1)
        set(value) = prefs.edit().putInt("routing_strategy_sticky_limit", value).apply()

    var routingStrategyComboRoundRobin: Boolean
        get() = prefs.getBoolean("routing_strategy_combo_round_robin", false)
        set(value) = prefs.edit().putBoolean("routing_strategy_combo_round_robin", value).apply()

    var routingStrategyComboStickyLimit: Int
        get() = prefs.getInt("routing_strategy_combo_sticky_limit", 1)
        set(value) = prefs.edit().putInt("routing_strategy_combo_sticky_limit", value).apply()
}
