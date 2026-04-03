package com.librechat.android.feature.chat.viewmodel.delegate

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.datastore.SettingsDataStore
import com.librechat.android.core.data.repository.SpeechRepository
import com.librechat.android.feature.chat.viewmodel.ChatStateHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TextToSpeechDelegate(
    private val stateHandle: ChatStateHandle,
    private val appContext: Context,
    private val speechRepository: SpeechRepository,
    private val settingsDataStore: SettingsDataStore,
    private val getMessageText: (String) -> String,
) {

    private var ttsEngine: TextToSpeech? = null
    private var ttsReady = false
    private var serverTtsPlayer: android.media.MediaPlayer? = null

    private fun getOrInitTts(onReady: () -> Unit) {
        if (ttsReady && ttsEngine != null) {
            onReady()
            return
        }
        ttsEngine?.shutdown()
        ttsEngine = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                ttsEngine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        stateHandle.update { copy(currentlyReadingMessageId = null) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        stateHandle.update {
                            copy(
                                currentlyReadingMessageId = null,
                                error = "Text-to-speech playback error",
                            )
                        }
                    }
                })
                onReady()
            } else {
                stateHandle.update {
                    copy(
                        currentlyReadingMessageId = null,
                        error = "Text-to-speech engine not available on this device",
                    )
                }
            }
        }
    }

    fun readAloud(messageId: String) {
        // If already reading this message, stop
        if (stateHandle.state.currentlyReadingMessageId == messageId) {
            stopReading()
            return
        }

        // Stop any current playback first
        stopReading()

        val text = getMessageText(messageId)
        if (text.isBlank()) return

        stateHandle.update { copy(currentlyReadingMessageId = messageId) }

        stateHandle.scope.launch {
            val source = settingsDataStore.ttsSource.first()
            if (source == "server") {
                readAloudViaServer(text)
            } else {
                val rate = settingsDataStore.ttsSpeechRate.first()
                val pitch = settingsDataStore.ttsPitch.first()
                val voiceName = settingsDataStore.ttsVoiceName.first()
                readAloudViaDevice(messageId, text, rate, pitch, voiceName)
            }
        }
    }

    internal fun readAloudViaDevice(
        messageId: String,
        text: String,
        speechRate: Float,
        pitch: Float,
        voiceName: String,
    ) {
        getOrInitTts {
            val engine = ttsEngine ?: return@getOrInitTts
            engine.setSpeechRate(speechRate)
            engine.setPitch(pitch)
            if (voiceName.isNotBlank()) {
                engine.voices?.find { it.name == voiceName }?.let { voice ->
                    engine.setVoice(voice)
                }
            }
            val params = android.os.Bundle()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, messageId)
        }
    }

    internal suspend fun readAloudViaServer(text: String) {
        when (val result = speechRepository.synthesizeSpeech(text)) {
            is Result.Success -> {
                try {
                    val audioBytes = result.data
                    val tempFile = java.io.File.createTempFile("tts_", ".mp3", appContext.cacheDir)
                    tempFile.deleteOnExit()
                    tempFile.writeBytes(audioBytes)

                    serverTtsPlayer?.release()
                    serverTtsPlayer = android.media.MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        setOnCompletionListener {
                            it.release()
                            serverTtsPlayer = null
                            tempFile.delete()
                            stateHandle.update { copy(currentlyReadingMessageId = null) }
                        }
                        setOnErrorListener { mp, _, _ ->
                            mp.release()
                            serverTtsPlayer = null
                            tempFile.delete()
                            stateHandle.update {
                                copy(
                                    currentlyReadingMessageId = null,
                                    error = "Server TTS playback failed",
                                )
                            }
                            true
                        }
                        prepare()
                        start()
                    }
                } catch (e: Exception) {
                    stateHandle.update {
                        copy(
                            currentlyReadingMessageId = null,
                            error = "Server TTS playback failed: ${e.message}",
                        )
                    }
                }
            }
            is Result.Error -> {
                stateHandle.update {
                    copy(
                        currentlyReadingMessageId = null,
                        error = result.message ?: "Server TTS request failed",
                    )
                }
            }
            is Result.Loading -> { /* no-op */ }
        }
    }

    fun stopReading() {
        ttsEngine?.stop()
        serverTtsPlayer?.let {
            it.stop()
            it.release()
        }
        serverTtsPlayer = null
        stateHandle.update { copy(currentlyReadingMessageId = null) }
    }

    /**
     * Checks the auto-read preference and, if enabled, reads the completed AI
     * response aloud via TTS. Skips auto-read when the user has already started
     * typing a new message (inputText is not blank).
     *
     * Uses a synthetic message ID ("auto_read") because the real message ID
     * may not be available yet (Room observer hasn't emitted the final message).
     */
    fun maybeAutoReadResponse(responseText: String) {
        // Don't auto-read if the user has already started typing
        if (stateHandle.state.inputText.isNotBlank()) return

        stateHandle.scope.launch {
            val autoRead = settingsDataStore.autoReadEnabled.first()
            if (!autoRead) return@launch

            val syntheticId = "auto_read_${System.currentTimeMillis()}"
            stateHandle.update { copy(currentlyReadingMessageId = syntheticId) }

            val source = settingsDataStore.ttsSource.first()
            if (source == "server") {
                readAloudViaServer(responseText)
            } else {
                val rate = settingsDataStore.ttsSpeechRate.first()
                val pitch = settingsDataStore.ttsPitch.first()
                val voiceName = settingsDataStore.ttsVoiceName.first()
                readAloudViaDevice(syntheticId, responseText, rate, pitch, voiceName)
            }
        }
    }

    fun release() {
        ttsEngine?.shutdown()
        ttsEngine = null
        ttsReady = false
        serverTtsPlayer?.release()
        serverTtsPlayer = null
    }
}
