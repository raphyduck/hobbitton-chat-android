package com.librechat.android.feature.settings.viewmodel.delegate

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.datastore.SettingsDataStore
import com.librechat.android.core.data.repository.SpeechRepository
import com.librechat.android.core.model.speech.TtsVoice
import com.librechat.android.feature.settings.screen.DeviceVoiceInfo
import com.librechat.android.feature.settings.viewmodel.SettingsStateHandle
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handles TTS voice selection, test playback, device voice loading, and MediaPlayer lifecycle.
 */
class SpeechSettingsDelegate(
    private val stateHandle: SettingsStateHandle,
    private val context: Context,
    private val speechRepository: SpeechRepository,
    private val settingsDataStore: SettingsDataStore,
) {

    private var currentMediaPlayer: MediaPlayer? = null
    private var deviceTtsEngine: TextToSpeech? = null

    fun loadVoices() {
        stateHandle.scope.launch {
            when (val result = speechRepository.getVoices()) {
                is Result.Success -> {
                    stateHandle.update { copy(availableVoices = result.data) }
                }
                is Result.Error -> {
                    Timber.d(result.exception, "Failed to load voices: ${result.message}")
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun loadDeviceVoices() {
        deviceTtsEngine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val voices = deviceTtsEngine?.voices?.map { voice ->
                    DeviceVoiceInfo(
                        name = voice.name,
                        locale = voice.locale.displayName,
                    )
                }?.sortedBy { it.name } ?: emptyList()
                stateHandle.update { copy(availableDeviceVoices = voices) }
            }
        }
    }

    fun setAutoSendAfterStt(enabled: Boolean) {
        stateHandle.scope.launch {
            settingsDataStore.setAutoSendAfterStt(enabled)
        }
    }

    fun setAutoReadEnabled(enabled: Boolean) {
        stateHandle.scope.launch {
            settingsDataStore.setAutoReadEnabled(enabled)
        }
    }

    fun selectVoice(voice: TtsVoice) {
        stateHandle.scope.launch {
            settingsDataStore.setSelectedVoiceId(voice.id)
        }
    }

    fun testVoice() {
        val voice = stateHandle.state.selectedVoice
        stateHandle.scope.launch {
            val result = speechRepository.synthesizeSpeech(
                text = "This is a test of the selected voice.",
                voice = voice?.id,
            )
            when (result) {
                is Result.Success -> {
                    playAudioBytes(result.data)
                }
                is Result.Error -> {
                    stateHandle.update { copy(error = result.message ?: "Voice test failed") }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun previewDeviceTts(text: String, rate: Float, pitch: Float, voiceName: String?) {
        stopTtsPreview()
        val tts = deviceTtsEngine ?: return
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        if (!voiceName.isNullOrBlank()) {
            val targetVoice = tts.voices?.find { it.name == voiceName }
            if (targetVoice != null) {
                tts.voice = targetVoice
            }
        }
        stateHandle.update { copy(isTtsPreviewPlaying = true) }
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { /* no-op */ }
            override fun onDone(utteranceId: String?) {
                stateHandle.update { copy(isTtsPreviewPlaying = false) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                stateHandle.update { copy(isTtsPreviewPlaying = false) }
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_preview")
    }

    fun previewServerTts(text: String, voice: String?, model: String?) {
        stopTtsPreview()
        stateHandle.update { copy(isTtsPreviewPlaying = true) }
        stateHandle.scope.launch {
            val result = speechRepository.synthesizeSpeech(
                text = text,
                voice = voice,
                model = model,
            )
            when (result) {
                is Result.Success -> {
                    playAudioBytes(result.data, isPreview = true)
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isTtsPreviewPlaying = false,
                            error = result.message ?: "Voice preview failed",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun stopTtsPreview() {
        deviceTtsEngine?.stop()
        currentMediaPlayer?.release()
        currentMediaPlayer = null
        stateHandle.update { copy(isTtsPreviewPlaying = false) }
    }

    // STT detail dialogs

    fun showSttDetailDialog() {
        stateHandle.update { copy(showSttDetailDialog = true) }
    }

    fun dismissSttDetailDialog() {
        stateHandle.update { copy(showSttDetailDialog = false) }
    }

    fun saveSttSettings(engine: String, language: String) {
        stateHandle.update {
            copy(sttEngine = engine, sttLanguage = language, showSttDetailDialog = false)
        }
        stateHandle.scope.launch {
            settingsDataStore.setSttEngine(engine)
            settingsDataStore.setSttLanguage(language)
        }
    }

    fun showTtsDetailDialog() {
        stateHandle.update { copy(showTtsDetailDialog = true) }
    }

    fun dismissTtsDetailDialog() {
        stateHandle.update { copy(showTtsDetailDialog = false) }
    }

    fun saveTtsSettings(
        engine: String,
        voice: String,
        rate: Float,
        pitch: Float,
        deviceVoiceName: String,
        caching: Boolean,
        source: String,
    ) {
        stateHandle.update {
            copy(
                ttsEngine = engine,
                ttsVoice = voice,
                ttsCaching = caching,
                ttsSource = source,
                showTtsDetailDialog = false,
            )
        }
        stateHandle.scope.launch {
            settingsDataStore.setTtsSource(source)
            settingsDataStore.setTtsSpeechRate(rate)
            settingsDataStore.setTtsPitch(pitch)
            settingsDataStore.setTtsVoiceName(deviceVoiceName)
            settingsDataStore.setTtsEngine(engine)
            settingsDataStore.setTtsVoice(voice)
            settingsDataStore.setTtsCaching(caching)
        }
    }

    /**
     * Plays audio bytes via MediaPlayer. Attaches listeners before calling prepare()
     * and wraps prepare/start in try-catch to release on failure (fixes resource leak).
     */
    private fun playAudioBytes(audioBytes: ByteArray, isPreview: Boolean = false) {
        try {
            // Stop any currently playing audio
            currentMediaPlayer?.release()
            currentMediaPlayer = null

            // Write bytes to a temporary file
            val tempFile = java.io.File.createTempFile("voice_test", ".mp3", context.cacheDir)
            tempFile.deleteOnExit()
            tempFile.writeBytes(audioBytes)

            // Create and configure MediaPlayer
            val mediaPlayer = MediaPlayer()
            // Attach listeners BEFORE calling prepare() to avoid resource leak
            mediaPlayer.setDataSource(tempFile.absolutePath)
            mediaPlayer.setOnCompletionListener {
                it.release()
                currentMediaPlayer = null
                tempFile.delete()
                if (isPreview) {
                    stateHandle.update { copy(isTtsPreviewPlaying = false) }
                }
            }
            mediaPlayer.setOnErrorListener { mp, what, extra ->
                Timber.e("MediaPlayer error: what=$what, extra=$extra")
                mp.release()
                currentMediaPlayer = null
                tempFile.delete()
                stateHandle.update {
                    copy(error = "Audio playback failed", isTtsPreviewPlaying = false)
                }
                true
            }
            try {
                mediaPlayer.prepare()
                mediaPlayer.start()
            } catch (e: Exception) {
                // Release MediaPlayer if prepare() or start() throws
                mediaPlayer.release()
                tempFile.delete()
                throw e
            }

            currentMediaPlayer = mediaPlayer
        } catch (e: Exception) {
            Timber.e(e, "Failed to play audio")
            stateHandle.update {
                copy(
                    error = "Audio playback failed: ${e.localizedMessage}",
                    isTtsPreviewPlaying = false,
                )
            }
        }
    }

    /**
     * Releases TTS engine and media player resources. Call from ViewModel.onCleared().
     */
    fun release() {
        currentMediaPlayer?.release()
        currentMediaPlayer = null
        deviceTtsEngine?.shutdown()
        deviceTtsEngine = null
    }
}
