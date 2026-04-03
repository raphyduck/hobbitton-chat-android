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
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

expect val authPlatformModule: Module

val authModule = module {
    includes(authPlatformModule)
    viewModelOf(::ServerUrlViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::VerifyEmailViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::ResetPasswordViewModel)
    viewModelOf(::TwoFactorViewModel)
    viewModelOf(::TermsViewModel)
}
