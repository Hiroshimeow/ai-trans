package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecordingService : Service() {
    private var localSpeechHelper: OnDeviceSpeechHelper? = null
    private var segmentIndex = 0

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val ACTION_RECORDING_STARTED = "com.example.audio.RECORDING_STARTED"
        const val ACTION_RECORDING_COMPLETED = "com.example.audio.RECORDING_COMPLETED"
        const val EXTRA_FILE_PATH = "EXTRA_FILE_PATH"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "recording_channel"
        
        var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        serviceScope.launch {
            RecordingController.getInstance(this@RecordingService).events.collect { event ->
                when (event) {
                    is RecordingEvent.AutoStopped -> {
                        val file = event.file
                        val sessionId = event.sessionId
                        
                        localSpeechHelper?.stopListening()
                        localSpeechHelper = null
                        segmentIndex = 0
                        
                        if (file != null && sessionId != null) {
                            val sampleRate = AudioRecorderHelper.getInstance(this@RecordingService).sampleRate
                            val bytesPerSec = sampleRate * 2.0
                            val durationSecs = file.length() / bytesPerSec
                            val db = com.example.data.AppDatabase.getDatabase(this@RecordingService)
                            val sessionEntity = db.recordingSessionDao().getRecordingSessionById(sessionId)
                            if (sessionEntity != null) {
                                val transcriptText = db.transcriptSegmentDao()
                                    .getSegmentsForSessionSync(sessionId)
                                    .filter { it.isFinal }
                                    .sortedBy { it.index }
                                    .joinToString("\n") { it.text }
                                
                                db.recordingSessionDao().updateRecordingSession(
                                    sessionEntity.copy(
                                        status = "completed",
                                        stopReason = "auto_silence",
                                        endedAt = System.currentTimeMillis(),
                                        durationMs = (durationSecs * 1000).toLong(),
                                        audioPath = file.absolutePath,
                                        finalTranscript = transcriptText.ifBlank { sessionEntity.finalTranscript }
                                    )
                                )

                                val recording = com.example.data.RecordingEntity(
                                    id = sessionId,
                                    audioPath = file.absolutePath,
                                    durationSeconds = durationSecs,
                                    transcript = "",
                                    createdAt = System.currentTimeMillis(),
                                    error = null
                                )
                                db.recordingDao().insertRecording(recording)
                                
                                val broadcastIntent = Intent(ACTION_RECORDING_COMPLETED)
                                broadcastIntent.putExtra("EXTRA_FILE_PATH", file.absolutePath)
                                broadcastIntent.putExtra("EXTRA_SESSION_ID", sessionId)
                                sendBroadcast(broadcastIntent)
                            }
                        }
                        stopForeground(true)
                        stopSelf()
                    }
                    is RecordingEvent.Error -> {
                        val broadcastIntent = Intent(ACTION_RECORDING_COMPLETED).apply {
                            putExtra("ERROR_MESSAGE", event.message)
                        }
                        sendBroadcast(broadcastIntent)
                        stopForeground(true)
                        stopSelf()
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isServiceRunning = true
                startForeground(NOTIFICATION_ID, createNotification())
                
                val runtimeRepo = com.example.data.RuntimeConfigRepository(this)
                val config = try { runtimeRepo.loadConfig() } catch (e: Exception) { null }
                val controller = RecordingController.getInstance(this)
                if (config == null || config.recording.meeting.sampleRate <= 0) {
                     controller.failRecording("ConfigError: Missing valid recording sampleRate configuration.")
                     stopSelf()
                     return START_NOT_STICKY
                }
                val sampleRate = config.recording.meeting.sampleRate
                val sessionId = intent.getStringExtra("EXTRA_SESSION_ID") ?: ""
                val isMeeting = intent.getBooleanExtra("EXTRA_IS_MEETING", true)
                val autoStop = intent.getBooleanExtra("EXTRA_AUTO_STOP", false)
                val threshold = intent.getFloatExtra("EXTRA_THRESHOLD", 0.012f)
                val maxSecs = intent.getIntExtra("EXTRA_MAX_SECS", 3)

                if (controller.recordingState.value.state == RecordingState.IDLE) {
                    if (isMeeting) {
                        controller.startMeeting(sessionId, sampleRate)
                    } else {
                        controller.startQuickVoice(sessionId, sampleRate, autoStop, threshold, maxSecs)
                    }
                    
                    val useLocal = com.example.data.SettingsManager(this).useOnDeviceRecognizer || isMeeting
                    if (useLocal) {
                        localSpeechHelper = OnDeviceSpeechHelper(this,
                            onEvent = { event ->
                                when (event) {
                                    is TranscriptEvent.Partial -> {
                                        saveLiveTranscriptSegment(sessionId, event.text, false, segmentIndex)
                                    }
                                    is TranscriptEvent.Final -> {
                                        saveLiveTranscriptSegment(sessionId, event.text, true, segmentIndex)
                                        segmentIndex++
                                    }
                                }
                            },
                            onError = { }
                        )
                        localSpeechHelper?.startListening(com.example.data.SettingsManager(this).speechLanguage)
                    }
                    
                    val file = AudioRecorderHelper.getInstance(this).currentFile
                    if (file != null) {
                        val broadcastIntent = Intent(ACTION_RECORDING_STARTED)
                        broadcastIntent.putExtra(EXTRA_FILE_PATH, file.absolutePath)
                        sendBroadcast(broadcastIntent)
                    }
                }
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    val controller = RecordingController.getInstance(this@RecordingService)
                    val currentState = controller.recordingState.value.state
                    val sessionId = controller.recordingState.value.sessionId
                    val durationMs = controller.recordingState.value.durationMs
                    
                    if (currentState == RecordingState.MEETING || currentState == RecordingState.QUICK_VOICE) {
                        val result = controller.stopAndFinalize()
                        val db = com.example.data.AppDatabase.getDatabase(this@RecordingService)
                        
                        localSpeechHelper?.stopListening()
                        localSpeechHelper = null
                        segmentIndex = 0
                        
                        when (result) {
                            is AudioRecorderHelper.FinalizeResult.Success -> {
                                val file = result.file
                                if (sessionId != null) {
                                    val sampleRate = AudioRecorderHelper.getInstance(this@RecordingService).sampleRate
                                    val bytesPerSec = sampleRate * 2.0
                                    val durationSecs = if (durationMs > 0) durationMs / 1000.0 else file.length() / bytesPerSec
                                    val finalDurationMs = if (durationMs > 0) durationMs else (durationSecs * 1000).toLong()
                                    
                                    val sessionEntity = db.recordingSessionDao().getRecordingSessionById(sessionId)
                                    if (sessionEntity != null) {
                                        val transcriptText = db.transcriptSegmentDao()
                                            .getSegmentsForSessionSync(sessionId)
                                            .filter { it.isFinal }
                                            .sortedBy { it.index }
                                            .joinToString("\n") { it.text }
        
                                        db.recordingSessionDao().updateRecordingSession(
                                            sessionEntity.copy(
                                                status = "completed",
                                                stopReason = "manual",
                                                endedAt = System.currentTimeMillis(),
                                                durationMs = finalDurationMs,
                                                audioPath = file.absolutePath,
                                                finalTranscript = transcriptText.ifBlank { sessionEntity.finalTranscript }
                                            )
                                        )
        
                                        val recording = com.example.data.RecordingEntity(
                                            id = sessionId,
                                            audioPath = file.absolutePath,
                                            durationSeconds = durationSecs,
                                            transcript = "",
                                            createdAt = System.currentTimeMillis(),
                                            error = null
                                        )
                                        db.recordingDao().insertRecording(recording)
                                    }
                                    
                                    val broadcastIntent = Intent(ACTION_RECORDING_COMPLETED)
                                    broadcastIntent.putExtra(EXTRA_FILE_PATH, file.absolutePath)
                                    broadcastIntent.putExtra("EXTRA_SESSION_ID", sessionId)
                                    sendBroadcast(broadcastIntent)
                                }
                            }
                            is AudioRecorderHelper.FinalizeResult.Timeout -> {
                                if (sessionId != null) {
                                    val sessionEntity = db.recordingSessionDao().getRecordingSessionById(sessionId)
                                    if (sessionEntity != null) {
                                        db.recordingSessionDao().updateRecordingSession(
                                            sessionEntity.copy(
                                                status = "failed",
                                                stopReason = "timeout",
                                                endedAt = System.currentTimeMillis(),
                                                errorCode = "RecordingFinalizeTimeout"
                                            )
                                        )
                                    }
                                    val broadcastIntent = Intent(ACTION_RECORDING_COMPLETED)
                                    broadcastIntent.putExtra("ERROR_MESSAGE", "Finalization Timeout")
                                    broadcastIntent.putExtra("EXTRA_SESSION_ID", sessionId)
                                    sendBroadcast(broadcastIntent)
                                }
                            }
                            is AudioRecorderHelper.FinalizeResult.Error -> {}
                        }
                    }
                    stopForeground(true)
                    stopSelf()
                }
                isServiceRunning = false
            }
            ACTION_CANCEL -> {
                localSpeechHelper?.stopListening()
                localSpeechHelper = null
                segmentIndex = 0
                
                val controller = RecordingController.getInstance(this@RecordingService)
                val sessionId = controller.recordingState.value.sessionId
                controller.cancelRecording()
                
                if (sessionId != null) {
                    serviceScope.launch {
                        val db = com.example.data.AppDatabase.getDatabase(this@RecordingService)
                        val sessionEntity = db.recordingSessionDao().getRecordingSessionById(sessionId)
                        if (sessionEntity != null) {
                            db.recordingSessionDao().updateRecordingSession(
                                sessionEntity.copy(
                                    status = "cancelled",
                                    stopReason = "cancelled",
                                    endedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
                
                isServiceRunning = false
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps microphone active for meeting transcriptions"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Chat Workspace")
            .setContentText("Recording in progress...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun saveLiveTranscriptSegment(sessionId: String, text: String, isFinal: Boolean, index: Int) {
         serviceScope.launch {
             val db = com.example.data.AppDatabase.getDatabase(this@RecordingService)
             db.transcriptSegmentDao().insertSegments(
                 listOf(
                     com.example.data.TranscriptSegmentEntity(
                         id = "${sessionId}_seg_${index}",
                         recordingSessionId = sessionId,
                         index = index,
                         startMs = null,
                         endMs = null,
                         text = text,
                         isFinal = isFinal,
                         speakerLabel = null,
                         language = null,
                         confidence = null,
                         source = "on_device_live",
                         createdAt = System.currentTimeMillis()
                     )
                 )
             )
         }
    }
}
