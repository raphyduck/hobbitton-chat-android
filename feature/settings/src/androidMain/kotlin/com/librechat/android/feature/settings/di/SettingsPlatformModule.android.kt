package com.librechat.android.feature.settings.di

import com.librechat.android.feature.settings.util.AndroidCacheCleaner
import com.librechat.android.feature.settings.util.AndroidContentReader
import com.librechat.android.feature.settings.util.ContentReader
import com.librechat.android.feature.settings.util.PlatformCacheCleaner
import com.librechat.android.feature.settings.viewmodel.delegate.SpeechSettingsDelegate
import com.librechat.android.feature.settings.viewmodel.delegate.SpeechSettingsFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val settingsPlatformModule: Module = module {
    single { AndroidContentReader(androidContext()) } bind ContentReader::class
    single { AndroidCacheCleaner(androidContext()) } bind PlatformCacheCleaner::class
    single<SpeechSettingsFactory> {
        SpeechSettingsFactory { stateHandle ->
            SpeechSettingsDelegate(
                stateHandle = stateHandle,
                context = androidContext(),
                speechRepository = get(),
                settingsDataStore = get(),
            )
        }
    }
}
