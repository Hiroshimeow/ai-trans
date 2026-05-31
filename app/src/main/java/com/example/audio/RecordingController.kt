package com.example.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class RecordingController private constructor(context: Context) {
    private val recorderHelper = AudioRecorderHelper.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // Explicit states
    private val _recordingState = MutableStateFlow(RecordingStatus())
    val recordingState: StateFlow<RecordingStatus> = _recordingState.asStateFlow()
    
    // We can bridge the callback from AudioRecorderHelper
    var onAutoStoppedListener: ((File) -> Unit)? = null
    var onErrorListener: ((String) -> Unit)? = null
    var onSilenceStopped: (() -> Unit)? = null

    init {
        recorderHelper.onAutoStoppedListener = { file ->
            _recordingState.value = _recordingState.value.copy(
                state = RecordingState.IDLE
            )
            onAutoStoppedListener?.invoke(file)
        }
        
        recorderHelper.onErrorListener = { error ->
            _recordingState.value = _recordingState.value.copy(
                error = error,
                state = RecordingState.IDLE
            )
            onErrorListener?.invoke(error)
        }
    }

    fun startMeeting(sessionId: String, sampleRate: Int) {
        if (_recordingState.value.state != RecordingState.IDLE) return
        
        recorderHelper.isSilenceDetectionEnabled = false
        recorderHelper.sampleRate = sampleRate
        
        val file = recorderHelper.startRecording()
        if (file != null) {
            _recordingState.value = _recordingState.value.copy(
                state = RecordingState.MEETING,
                sessionId = sessionId,
                error = null
            )
            
            // Poll for duration and rms
            scope.launch {
                while (_recordingState.value.state == RecordingState.MEETING) {
                    _recordingState.value = _recordingState.value.copy(
                        durationMs = recorderHelper.durationMs,
                        rmsAmplitude = recorderHelper.currentRms
                    )
                    kotlinx.coroutines.delay(200)
                }
            }
        }
    }
    
    fun startQuickVoice(sessionId: String, sampleRate: Int, autoStop: Boolean, threshold: Float, maxSeconds: Int) {
        if (_recordingState.value.state != RecordingState.IDLE) return
        
        recorderHelper.isSilenceDetectionEnabled = autoStop
        recorderHelper.silenceThreshold = threshold
        recorderHelper.maxSilenceSeconds = maxSeconds
        recorderHelper.sampleRate = sampleRate
        
        val file = recorderHelper.startRecording()
        if (file != null) {
            _recordingState.value = _recordingState.value.copy(
                state = RecordingState.QUICK_VOICE,
                sessionId = sessionId,
                error = null
            )
            
            scope.launch {
                while (_recordingState.value.state == RecordingState.QUICK_VOICE) {
                    _recordingState.value = _recordingState.value.copy(
                        durationMs = recorderHelper.durationMs,
                        rmsAmplitude = recorderHelper.currentRms
                    )
                    
                    if (!recorderHelper.isRecording && recorderHelper.durationMs > 0) {
                        _recordingState.value = _recordingState.value.copy(state = RecordingState.IDLE)
                        onSilenceStopped?.invoke()
                        break
                    }
                    kotlinx.coroutines.delay(100)
                }
            }
        }
    }

    suspend fun stopAndFinalize(): File? {
        val st = _recordingState.value.state
        if (st == RecordingState.IDLE || st == RecordingState.STOPPING) return null
        
        _recordingState.value = _recordingState.value.copy(state = RecordingState.STOPPING)
        return recorderHelper.stopAndFinalize()
    }
    
    fun cancelRecording() {
        val file = recorderHelper.stopRecording()
        if (file != null && file.exists()) {
            file.delete()
        }
        _recordingState.value = RecordingStatus(state = RecordingState.IDLE)
    }

    companion object {
        @Volatile
        private var instance: RecordingController? = null
        
        fun getInstance(context: Context): RecordingController {
            return instance ?: synchronized(this) {
                instance ?: RecordingController(context.applicationContext).also { instance = it }
            }
        }
    }
}
