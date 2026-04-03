package com.garfiec.librechat.feature.chat.viewmodel.delegate

/**
 * Platform-abstracted voice input handling.
 * Android: wraps VoiceInputDelegate with MediaRecorder.
 * iOS: no-op initially (voice input not yet supported on iOS).
 */
interface PlatformVoiceInput {
    fun startRecording()
    fun stopRecording()
    fun cancelRecording()
    fun onDeviceSpeechResult(transcribedText: String)
    fun loadSpeechConfig()
    fun release()
}
