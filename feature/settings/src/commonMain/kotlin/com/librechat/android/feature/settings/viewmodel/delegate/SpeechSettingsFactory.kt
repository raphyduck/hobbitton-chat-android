package com.librechat.android.feature.settings.viewmodel.delegate

import com.librechat.android.feature.settings.viewmodel.SettingsStateHandle

fun interface SpeechSettingsFactory {
    fun create(stateHandle: SettingsStateHandle): SpeechSettingsContract
}
