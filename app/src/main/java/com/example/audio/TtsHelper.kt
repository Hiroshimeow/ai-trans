package com.example.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsHelper(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null
    private var pendingLang: String? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("TtsHelper", "Initialization of TextToSpeech failed", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            Log.d("TtsHelper", "TTS Engine successfully initialized")
            val text = pendingText
            val lang = pendingLang
            if (text != null && lang != null) {
                speak(text, lang)
                pendingText = null
                pendingLang = null
            }
        } else {
            Log.e("TtsHelper", "Failed to initialize TTS local engine")
        }
    }

    fun speak(text: String, languageCode: String, speechRate: Float = 1.0f, pitch: Float = 1.0f) {
        val engine = tts
        if (engine == null) {
            Log.w("TtsHelper", "TTS instance is null")
            return
        }

        if (!isInitialized) {
            pendingText = text
            pendingLang = languageCode
            return
        }

        val locale = if (languageCode.startsWith("vi", ignoreCase = true)) {
            Locale("vi")
        } else if (languageCode.startsWith("ja", ignoreCase = true)) {
            Locale.JAPANESE
        } else {
            Locale.ENGLISH
        }

        try {
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TtsHelper", "Language $locale is not supported or lacks voice files. Attempting standard default.")
                engine.setLanguage(Locale.getDefault())
            }

            engine.setSpeechRate(speechRate)
            engine.setPitch(pitch)

            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ScreenChatTtsID")
            Log.d("TtsHelper", "Speaking content in locale: $locale with rate: $speechRate, pitch: $pitch")
        } catch (e: Exception) {
            Log.e("TtsHelper", "TTS speech execution error: ${e.message}")
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("TtsHelper", "TTS stop failed", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("TtsHelper", "TTS shutdown failed", e)
        }
    }
}
