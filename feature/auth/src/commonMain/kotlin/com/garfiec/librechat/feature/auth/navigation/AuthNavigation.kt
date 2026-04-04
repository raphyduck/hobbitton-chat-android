package com.garfiec.librechat.feature.auth.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.auth.screen.ForgotPasswordScreen
import com.garfiec.librechat.feature.auth.screen.LoginScreen
import com.garfiec.librechat.feature.auth.screen.RegisterScreen
import com.garfiec.librechat.feature.auth.screen.ResetPasswordScreen
import com.garfiec.librechat.feature.auth.screen.ServerUrlScreen
import com.garfiec.librechat.feature.auth.screen.TermsScreen
import com.garfiec.librechat.feature.auth.screen.TwoFactorScreen
import com.garfiec.librechat.feature.auth.screen.VerifyEmailScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface AuthRoute : NavKey

@Serializable data object ServerUrl : AuthRoute
@Serializable data object Login : AuthRoute
@Serializable data object Register : AuthRoute
@Serializable data object ForgotPassword : AuthRoute
@Serializable data class TwoFactor(val tempToken: String) : AuthRoute
@Serializable data class VerifyEmail(val email: String) : AuthRoute
@Serializable data object Terms : AuthRoute
@Serializable data class ResetPassword(val userId: String, val token: String) : AuthRoute

fun EntryProviderScope<NavKey>.authEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onAuthComplete: () -> Unit,
) {
    entry<ServerUrl> {
        ServerUrlScreen(
            onServerValidated = { onNavigate(Login) },
        )
    }
    entry<Login> {
        LoginScreen(
            onLoginSuccess = onAuthComplete,
            onNavigateToRegister = { onNavigate(Register) },
            onNavigateToForgotPassword = { onNavigate(ForgotPassword) },
            onNavigateToTwoFactor = { tempToken ->
                onNavigate(TwoFactor(tempToken = tempToken))
            },
        )
    }
    entry<Register> {
        RegisterScreen(
            onRegistered = onBack,
            onNavigateToLogin = onBack,
        )
    }
    entry<ForgotPassword> {
        ForgotPasswordScreen(
            onBack = onBack,
        )
    }
    entry<TwoFactor> { key ->
        TwoFactorScreen(
            onVerified = onAuthComplete,
            onBack = onBack,
            tempToken = key.tempToken,
        )
    }
    entry<VerifyEmail> { key ->
        VerifyEmailScreen(
            onVerified = onBack,
            onBack = onBack,
            email = key.email,
        )
    }
    entry<Terms> {
        TermsScreen(
            onAccepted = onAuthComplete,
            onBack = onBack,
        )
    }
    entry<ResetPassword> { key ->
        ResetPasswordScreen(
            onResetComplete = onBack,
            onBack = onBack,
            userId = key.userId,
            token = key.token,
        )
    }
}

val authSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(ServerUrl::class, ServerUrl.serializer())
        subclass(Login::class, Login.serializer())
        subclass(Register::class, Register.serializer())
        subclass(ForgotPassword::class, ForgotPassword.serializer())
        subclass(TwoFactor::class, TwoFactor.serializer())
        subclass(VerifyEmail::class, VerifyEmail.serializer())
        subclass(Terms::class, Terms.serializer())
        subclass(ResetPassword::class, ResetPassword.serializer())
    }
}
