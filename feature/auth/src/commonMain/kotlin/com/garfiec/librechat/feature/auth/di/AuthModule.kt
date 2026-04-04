package com.garfiec.librechat.feature.auth.di

import com.garfiec.librechat.feature.auth.viewmodel.ForgotPasswordViewModel
import com.garfiec.librechat.feature.auth.viewmodel.LoginViewModel
import com.garfiec.librechat.feature.auth.viewmodel.RegisterViewModel
import com.garfiec.librechat.feature.auth.viewmodel.ResetPasswordViewModel
import com.garfiec.librechat.feature.auth.viewmodel.ServerUrlViewModel
import com.garfiec.librechat.feature.auth.viewmodel.TermsViewModel
import com.garfiec.librechat.feature.auth.viewmodel.TwoFactorViewModel
import com.garfiec.librechat.feature.auth.viewmodel.VerifyEmailViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

expect val authPlatformModule: Module

val authModule = module {
    includes(authPlatformModule)
    viewModelOf(::ServerUrlViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModel { params ->
        VerifyEmailViewModel(
            savedStateHandle = get(),
            userRepository = get(),
            initialEmail = params.getOrNull(),
        )
    }
    viewModelOf(::ForgotPasswordViewModel)
    viewModel { params ->
        ResetPasswordViewModel(
            savedStateHandle = get(),
            authRepository = get(),
            initialUserId = params.getOrNull(),
            initialToken = params.getOrNull(),
        )
    }
    viewModel { params ->
        TwoFactorViewModel(
            savedStateHandle = get(),
            authRepository = get(),
            initialTempToken = params.getOrNull(),
        )
    }
    viewModelOf(::TermsViewModel)
}
