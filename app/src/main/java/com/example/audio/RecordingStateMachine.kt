package com.example.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RecordingState {
    IDLE,
    MEETING,
    QUICK_VOICE,
    STOPPING
}

data class RecordingStatus(
    val state: RecordingState = RecordingState.IDLE,
    val sessionId: String? = null,
    val durationMs: Long = 0,
    val rmsAmplitude: Float = 0f,
    val error: String? = null
)
