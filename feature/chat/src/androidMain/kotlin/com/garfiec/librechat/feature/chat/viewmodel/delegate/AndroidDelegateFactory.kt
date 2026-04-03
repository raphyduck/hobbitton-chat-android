package com.garfiec.librechat.feature.chat.viewmodel.delegate

import android.content.Context
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class AndroidDelegateFactory(
    private val appContext: Context,
    private val fileRepository: FileRepository,
    private val speechRepository: SpeechRepository,
    private val settingsDataStore: SettingsDataStore,
) : PlatformDelegateFactory {

    override fun createFileHandler(stateHandle: ChatStateHandle): PlatformFileHandler {
        return AndroidFileHandler(
            FileAttachmentDelegate(
                stateHandle = stateHandle,
                appContext = appContext,
                fileRepository = fileRepository,
            ),
        )
    }

    override fun createVoiceInput(
        stateHandle: ChatStateHandle,
        onTranscriptionComplete: () -> Unit,
    ): PlatformVoiceInput {
        return AndroidVoiceInput(
            VoiceInputDelegate(
                stateHandle = stateHandle,
                appContext = appContext,
                speechRepository = speechRepository,
                autoSendAfterStt = settingsDataStore.autoSendAfterStt
                    .stateIn(stateHandle.scope, SharingStarted.Eagerly, false),
                onTranscriptionComplete = onTranscriptionComplete,
            ),
        )
    }

    override fun createTts(
        stateHandle: ChatStateHandle,
        getMessageText: (String) -> String,
    ): PlatformTts {
        return AndroidTts(
            TextToSpeechDelegate(
                stateHandle = stateHandle,
                appContext = appContext,
                speechRepository = speechRepository,
                settingsDataStore = settingsDataStore,
                getMessageText = getMessageText,
            ),
        )
    }

    override fun createShareConsumer(): PlatformShareConsumer {
        return AndroidShareConsumer()
    }
}
