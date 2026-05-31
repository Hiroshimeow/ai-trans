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

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RECORDING_STARTED = "com.example.audio.RECORDING_STARTED"
        const val EXTRA_FILE_PATH = "EXTRA_FILE_PATH"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "recording_channel"
        
        var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isServiceRunning = true
                startForeground(NOTIFICATION_ID, createNotification())
                if (!AudioRecorderHelper.getInstance(this).isRecording) {
                    AudioRecorderHelper.getInstance(this).isSilenceDetectionEnabled = false
                    val file = AudioRecorderHelper.getInstance(this).startRecording()
                    if (file != null) {
                        val broadcastIntent = Intent(ACTION_RECORDING_STARTED)
                        broadcastIntent.putExtra(EXTRA_FILE_PATH, file.absolutePath)
                        sendBroadcast(broadcastIntent)
                    }
                }
            }
            ACTION_STOP -> {
                if (AudioRecorderHelper.getInstance(this).isRecording) {
                    AudioRecorderHelper.getInstance(this).stopRecording()
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Chat Workspace")
            .setContentText("Recording in progress...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
