package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle

/**
 * Factory that creates platform-specific delegates once the ChatStateHandle is available.
 * This is necessary because delegates need the ViewModel's state handle and coroutine scope,
 * which aren't available until the ViewModel is constructed.
 */
interface PlatformDelegateFactory {
    fun createFileHandler(stateHandle: ChatStateHandle): PlatformFileHandler
    fun createVoiceInput(
        stateHandle: ChatStateHandle,
        onTranscriptionComplete: () -> Unit,
    ): PlatformVoiceInput
    fun createTts(
        stateHandle: ChatStateHandle,
        getMessageText: (String) -> String,
    ): PlatformTts
    fun createShareConsumer(): PlatformShareConsumer
}
