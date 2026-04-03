package com.librechat.android.feature.auth.di

import com.librechat.android.feature.auth.oauth.AndroidOAuthLauncher
import com.librechat.android.feature.auth.oauth.OAuthLauncher
import org.koin.core.module.Module
import org.koin.dsl.module

actual val authPlatformModule: Module = module {
    single<OAuthLauncher> { AndroidOAuthLauncher(get()) }
}
