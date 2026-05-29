package com.example.audio

import android.media.MediaPlayer
import android.util.Log
import java.io.File

class AudioPlayerHelper {
    private val tag = "AudioPlayerHelper"
    private var mediaPlayer: MediaPlayer? = null
    var currentlyPlayingFilePath: String? = null
        private set

    var onPlaybackCompleteListener: (() -> Unit)? = null

    fun playAudio(file: File) {
        if (!file.exists()) {
            Log.e(tag, "Audio file to play does not exist: ${file.absolutePath}")
            return
        }

        stopAudio()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    stopAudio()
                    onPlaybackCompleteListener?.invoke()
                }
            }
            currentlyPlayingFilePath = file.absolutePath
            Log.d(tag, "Started playing audio: ${file.name}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to play audio: ${e.message}", e)
            stopAudio()
        }
    }

    fun stopAudio() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.e(tag, "MediaPlayer stop/release failed", e)
            }
        }
        mediaPlayer = null
        currentlyPlayingFilePath = null
    }

    fun isPlayingFile(filePath: String): Boolean {
        return currentlyPlayingFilePath == filePath && mediaPlayer?.isPlaying == true
    }
}
