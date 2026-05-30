package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class AudioRecorderHelper(
    private val context: Context
) {
    companion object {
        @Volatile
        private var instance: AudioRecorderHelper? = null
        
        fun getInstance(context: Context): AudioRecorderHelper {
            return instance ?: synchronized(this) {
                instance ?: AudioRecorderHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    private val tag = "AudioRecorderHelper"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    var isRecording = false
        private set

    // Real-time metrics
    var durationMs: Long = 0L
        private set
    var currentRms: Float = 0f
        private set

    // Configuration
    var isSilenceDetectionEnabled = true
    var silenceThreshold = 0.012f // RMS threshold
    var maxSilenceSeconds = 3
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var outputFile: File? = null

    // Callback
    var onAutoStoppedListener: ((File) -> Unit)? = null
    var onErrorListener: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording(): File? {
        if (isRecording) {
            Log.w(tag, "Recording already active.")
            return null
        }

        val baseDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val recordingsDir = File(baseDir, "ScreenChatRecordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }

        val file = File(recordingsDir, "recording_${System.currentTimeMillis()}.wav")
        outputFile = file

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            onErrorListener?.invoke("Unable to calculate min buffer size for AudioRecord.")
            return null
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onErrorListener?.invoke("AudioRecord initialization failed. Ensure permissions are granted.")
                return null
            }

            audioRecord?.startRecording()
            isRecording = true
            durationMs = 0L
            currentRms = 0f

            recordJob = scope.launch(Dispatchers.IO) {
                recordingLoop(file, minBufferSize)
            }

            Log.d(tag, "Recording started -> File: ${file.absolutePath}")
            return file

        } catch (e: SecurityException) {
            Log.e(tag, "Microphone permission denied by system.", e)
            onErrorListener?.invoke("Microphone permission denied. Enable microphone access under App Settings.")
            stopRecording()
            return null
        } catch (e: Exception) {
            Log.e(tag, "Exception during startRecording", e)
            onErrorListener?.invoke(e.message ?: "Failed to start audio recording.")
            stopRecording()
            return null
        }
    }

    private suspend fun recordingLoop(file: File, bufferSize: Int) {
        val fos = FileOutputStream(file)
        val shortBuffer = ShortArray(bufferSize)
        val byteBuffer = ByteBuffer.allocate(bufferSize * 2).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }

        // Write empty space for 44-byte WAV header
        fos.write(ByteArray(44))

        var totalBytesWritten = 0L
        val startTime = System.currentTimeMillis()
        var lastVoiceTime = System.currentTimeMillis()
        val gracePeriodMs = 1500L // 1.5 seconds start grace period before silence-trip is active

        try {
            while (isRecording) {
                val record = audioRecord ?: break
                val shortsRead = record.read(shortBuffer, 0, shortBuffer.size)

                if (shortsRead > 0) {
                    // Compute amplitude RMS for speaking detection
                    var sumSquare = 0.0
                    byteBuffer.clear()

                    for (i in 0 until shortsRead) {
                        val sh = shortBuffer[i]
                        byteBuffer.putShort(sh)

                        val normalized = sh / 32768.0
                        sumSquare += normalized * normalized
                    }

                    currentRms = sqrt(sumSquare / shortsRead).toFloat()

                    // Write binary PCM to output stream
                    fos.write(byteBuffer.array(), 0, shortsRead * 2)
                    totalBytesWritten += shortsRead * 2

                    val now = System.currentTimeMillis()
                    durationMs = now - startTime

                    // Silence recognition
                    if (currentRms >= silenceThreshold) {
                        lastVoiceTime = now
                    } else if (isSilenceDetectionEnabled && (now - startTime > gracePeriodMs)) {
                        val silenceDurationSecs = (now - lastVoiceTime) / 1000
                        if (silenceDurationSecs >= maxSilenceSeconds) {
                            Log.d(tag, "Silence threshold tripped. Auto-stopping...")
                            withContext(Dispatchers.Main) {
                                isRecording = false // triggers break from thread
                            }
                        }
                    }
                }
                delay(40) // polling throttle
            }
        } catch (e: Exception) {
            Log.e(tag, "Error in recording loop", e)
            withContext(Dispatchers.Main) {
                onErrorListener?.invoke("Recording thread error: ${e.message}")
            }
        } finally {
            withContext(NonCancellable) {
                try {
                    fos.close()
                } catch (e: Exception) {
                    Log.e(tag, "Failed to close fos", e)
                }
                // Overwrite correct WAV header size details
                writeWavHeaderDetails(file, totalBytesWritten)
                copyToSharedMediaStore(file)

                withContext(Dispatchers.Main) {
                    Log.d(tag, "Recording finished writing. Total audio payload bytes: $totalBytesWritten")
                    isRecording = false
                    audioRecord?.let {
                        try {
                            it.stop()
                            it.release()
                        } catch (e: Exception) {
                            Log.e(tag, "AudioRecord clean stop failed", e)
                        }
                    }
                    audioRecord = null
                    onAutoStoppedListener?.invoke(file)
                    outputFile = null
                }
            }
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) {
            return null
        }
        val fileTemp = outputFile
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        return fileTemp
    }

    private fun writeWavHeaderDetails(wavFile: File, totalAudioLen: Long) {
        if (!wavFile.exists()) return
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val longSampleRate = sampleRate.toLong()
        val byteRate = longSampleRate * channels * 2

        try {
            RandomAccessFile(wavFile, "rw").use { raf ->
                val header = ByteArray(44)
                header[0] = 'R'.code.toByte() // RIFF
                header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte()
                header[3] = 'F'.code.toByte()
                header[4] = (totalDataLen and 0xff).toByte()
                header[5] = ((totalDataLen shr 8) and 0xff).toByte()
                header[6] = ((totalDataLen shr 16) and 0xff).toByte()
                header[7] = ((totalDataLen shr 24) and 0xff).toByte()
                header[8] = 'W'.code.toByte() // WAVE
                header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte()
                header[11] = 'E'.code.toByte()
                header[12] = 'f'.code.toByte() // 'fmt ' chunk
                header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte()
                header[15] = ' '.code.toByte()
                header[16] = 16 // 4 bytes: size of 'fmt' chunk
                header[17] = 0
                header[18] = 0
                header[19] = 0
                header[20] = 1 // format = 1 (PCM)
                header[21] = 0
                header[22] = channels.toByte()
                header[23] = 0
                header[24] = (longSampleRate and 0xff).toByte()
                header[25] = ((longSampleRate shr 8) and 0xff).toByte()
                header[26] = ((longSampleRate shr 16) and 0xff).toByte()
                header[27] = ((longSampleRate shr 24) and 0xff).toByte()
                header[28] = (byteRate and 0xff).toByte()
                header[29] = ((byteRate shr 8) and 0xff).toByte()
                header[30] = ((byteRate shr 16) and 0xff).toByte()
                header[31] = ((byteRate shr 24) and 0xff).toByte()
                header[32] = (channels * 2).toByte() // block align
                header[33] = 0
                header[34] = 16 // bits per sample
                header[35] = 0
                header[36] = 'd'.code.toByte() // 'data' chunk
                header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte()
                header[39] = 'a'.code.toByte()
                header[40] = (totalAudioLen and 0xff).toByte()
                header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
                header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
                header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

                raf.seek(0)
                raf.write(header)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error overwriting WAV header details", e)
        }
    }

    private fun copyToSharedMediaStore(srcFile: File) {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, srcFile.name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_MUSIC + "/ScreenChatRecordings")
            }
        }
        try {
            val uri = resolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    srcFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                Log.d(tag, "Successfully copied recording to public MediaStore directory: $uri")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error saving copy to shared public MediaStore Music folder", e)
        }
    }
}
