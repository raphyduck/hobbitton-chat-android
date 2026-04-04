package com.garfiec.librechat.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.garfiec.librechat.core.ui.components.ScreenTransitionWrapper
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

@Serializable sealed interface AuthRoute

@Serializable data object ServerUrl : AuthRoute
@Serializable data object Login : AuthRoute
@Serializable data object Register : AuthRoute
@Serializable data object ForgotPassword : AuthRoute
@Serializable data class TwoFactor(val tempToken: String) : AuthRoute
@Serializable data class VerifyEmail(val email: String) : AuthRoute
@Serializable data object Terms : AuthRoute
@Serializable data class ResetPassword(val userId: String, val token: String) : AuthRoute

fun NavController.navigateToVerifyEmail(email: String) {
    navigate(VerifyEmail(email = email))
}

fun NavController.navigateToResetPassword(userId: String, token: String) {
    navigate(ResetPassword(userId = userId, token = token))
}

fun NavGraphBuilder.authGraph(
    navController: NavController,
    onAuthComplete: () -> Unit,
) {
    navigation<AuthRoute>(startDestination = ServerUrl::class) {
        composable<ServerUrl> {
            ScreenTransitionWrapper(transition) {
                ServerUrlScreen(
                    onServerValidated = { navController.navigate(Login) },
                )
            }
        }
        composable<Login> {
            ScreenTransitionWrapper(transition) {
                LoginScreen(
                    onLoginSuccess = onAuthComplete,
                    onNavigateToRegister = { navController.navigate(Register) },
                    onNavigateToForgotPassword = { navController.navigate(ForgotPassword) },
                    onNavigateToTwoFactor = { tempToken ->
                        navController.navigate(TwoFactor(tempToken = tempToken))
                    },
                )
            }
        }
        composable<Register> {
            ScreenTransitionWrapper(transition) {
                RegisterScreen(
                    onRegistered = { navController.popBackStack<Login>(inclusive = false) },
                    onNavigateToLogin = { navController.popBackStack() },
                )
            }
        }
        composable<ForgotPassword> {
            ScreenTransitionWrapper(transition) {
                ForgotPasswordScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable<TwoFactor> {
            ScreenTransitionWrapper(transition) {
                TwoFactorScreen(
                    onVerified = onAuthComplete,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable<VerifyEmail> {
            ScreenTransitionWrapper(transition) {
                VerifyEmailScreen(
                    onVerified = { navController.popBackStack<Login>(inclusive = false) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable<Terms> {
            ScreenTransitionWrapper(transition) {
                TermsScreen(
                    onAccepted = onAuthComplete,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable<ResetPassword> {
            ScreenTransitionWrapper(transition) {
                ResetPasswordScreen(
                    onResetComplete = { navController.popBackStack<Login>(inclusive = false) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

val authSerializersModule = SerializersModule {
    polymorphic(AuthRoute::class) {
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
