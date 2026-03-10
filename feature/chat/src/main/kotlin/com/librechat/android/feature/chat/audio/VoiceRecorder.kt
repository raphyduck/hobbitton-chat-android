package com.librechat.android.feature.chat.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Manages audio recording using Android's MediaRecorder.
 * Records to a temporary file in the app's cache directory, returning
 * the raw bytes when stopped. Uses OGG/Opus on API 29+ and 3GP/AMR_NB on older devices.
 */
class VoiceRecorder(private val context: Context) {

    companion object {
        private const val TAG = "VoiceRecorder"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isCurrentlyRecording = false

    val isRecording: Boolean
        get() = isCurrentlyRecording

    val mimeType: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "audio/ogg"
        } else {
            "audio/3gpp"
        }

    fun start() {
        if (isCurrentlyRecording) return

        val extension = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "ogg" else "3gp"
        outputFile = File(context.cacheDir, "voice_recording_${System.currentTimeMillis()}.$extension")

        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                }
                setAudioSamplingRate(16_000)
                setAudioChannels(1)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isCurrentlyRecording = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice recording", e)
            cleanup()
            throw e
        }
    }

    fun stop(): ByteArray? {
        if (!isCurrentlyRecording) return null

        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isCurrentlyRecording = false

            val file = outputFile
            val bytes = file?.readBytes()
            file?.delete()
            outputFile = null
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop voice recording", e)
            cleanup()
            null
        }
    }

    fun cancel() {
        cleanup()
    }

    private fun cleanup() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            // Recorder may not have been started
        }
        mediaRecorder = null
        isCurrentlyRecording = false
        outputFile?.delete()
        outputFile = null
    }
}
