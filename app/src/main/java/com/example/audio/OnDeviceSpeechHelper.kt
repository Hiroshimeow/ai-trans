package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class OnDeviceSpeechHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val segments = mutableListOf<String>()
    private var currentSegment = ""
    private var isRunning = false
    private var activeLanguageCode = "vi-VN"
    private val handler = Handler(Looper.getMainLooper())

    private val speechIntent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Dictation variables to enhance system sensitivity
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("OnDeviceSpeechHelper", "System recognizer ready.")
        }

        override fun onBeginningOfSpeech() {
            Log.d("OnDeviceSpeechHelper", "VAD started speaking.")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions missing"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No voice match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Service Busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (Voice silent)"
                else -> "Error code: $error"
            }
            Log.w("OnDeviceSpeechHelper", "SpeechRecognizer error callback: $message")
            
            if (isRunning) {
                // Instantly re-initiate speech recognition loop after short timeout
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    handler.postDelayed({ safeRecreateAndStart() }, 300)
                } else {
                    handler.postDelayed({ safeListen() }, 100)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                if (text.isNotBlank()) {
                    segments.add(text)
                }
            }
            currentSegment = ""
            notifyUpdate()

            if (isRunning) {
                handler.post({ safeListen() })
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                currentSegment = matches[0]
            }
            notifyUpdate()
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun safeListen() {
        if (!isRunning) return
        try {
            speechRecognizer?.stopListening()
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLanguageCode)
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, activeLanguageCode)
            speechRecognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            Log.e("OnDeviceSpeechHelper", "safeListen failure: ${e.message}")
            safeRecreateAndStart()
        }
    }

    private fun safeRecreateAndStart() {
        if (!isRunning) return
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("OnDeviceSpeechHelper", "Destruction exception: ${e.message}")
        }
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(listener)
            }
            safeListen()
        } catch (e: Exception) {
            Log.e("OnDeviceSpeechHelper", "Recreation failed: ${e.message}")
        }
    }

    private fun notifyUpdate() {
        val total = mutableListOf<String>()
        total.addAll(segments)
        if (currentSegment.isNotBlank()) {
            total.add(currentSegment)
        }
        val text = total.joinToString(". ")
        onResult(text)
    }

    fun startListening(languageCode: String = "vi-VN") {
        if (isRunning) return
        isRunning = true
        activeLanguageCode = languageCode
        segments.clear()
        currentSegment = ""
        safeRecreateAndStart()
    }

    fun stopListening() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("OnDeviceSpeechHelper", "Stop/destroy failed: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }
}
