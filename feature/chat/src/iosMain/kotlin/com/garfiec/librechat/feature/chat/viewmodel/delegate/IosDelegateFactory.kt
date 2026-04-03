package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle

class IosDelegateFactory(
    private val fileRepository: FileRepository,
    private val speechRepository: SpeechRepository,
    private val settingsDataStore: SettingsDataStore,
) : PlatformDelegateFactory {

    override fun createFileHandler(stateHandle: ChatStateHandle): PlatformFileHandler {
        return IosFileHandler(stateHandle, fileRepository)
    }

    override fun createVoiceInput(
        stateHandle: ChatStateHandle,
        onTranscriptionComplete: () -> Unit,
    ): PlatformVoiceInput {
        return IosVoiceInput(stateHandle, onTranscriptionComplete)
    }

    override fun createTts(
        stateHandle: ChatStateHandle,
        getMessageText: (String) -> String,
    ): PlatformTts {
        return IosTts(
            stateHandle = stateHandle,
            speechRepository = speechRepository,
            settingsDataStore = settingsDataStore,
            getMessageText = getMessageText,
        )
    }

    override fun createShareConsumer(): PlatformShareConsumer {
        return IosShareConsumer()
    }
}
