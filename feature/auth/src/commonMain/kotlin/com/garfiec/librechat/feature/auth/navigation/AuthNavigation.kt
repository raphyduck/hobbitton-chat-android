package com.garfiec.librechat.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
const val AUTH_GRAPH_ROUTE = "auth_graph"
const val SERVER_URL_ROUTE = "server_url"
const val LOGIN_ROUTE = "login"
const val REGISTER_ROUTE = "register"
const val FORGOT_PASSWORD_ROUTE = "forgot_password"
const val TWO_FACTOR_ROUTE = "two_factor/{tempToken}"
const val VERIFY_EMAIL_ROUTE = "verify_email/{email}"
const val TERMS_ROUTE = "auth/terms"
const val RESET_PASSWORD_ROUTE = "reset_password/{userId}/{token}"

fun NavController.navigateToVerifyEmail(email: String) {
    navigate("verify_email/${encodeNavArg(email)}")
}

fun NavController.navigateToResetPassword(userId: String, token: String) {
    navigate("reset_password/${encodeNavArg(userId)}/${encodeNavArg(token)}")
}

private fun encodeNavArg(value: String): String = buildString {
    for (c in value) {
        when {
            c.isLetterOrDigit() || c in "-._~" -> append(c)
            else -> {
                val bytes = c.toString().encodeToByteArray()
                for (b in bytes) {
                    append('%')
                    append(
                        (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'),
                    )
                }
            }
        }
    }
}

fun NavGraphBuilder.authGraph(
    navController: NavController,
    onAuthComplete: () -> Unit,
) {
    navigation(startDestination = SERVER_URL_ROUTE, route = AUTH_GRAPH_ROUTE) {
        composable(SERVER_URL_ROUTE) {
            ScreenTransitionWrapper(transition) {
                ServerUrlScreen(
                    onServerValidated = { navController.navigate(LOGIN_ROUTE) },
                )
            }
        }
        composable(LOGIN_ROUTE) {
            ScreenTransitionWrapper(transition) {
                LoginScreen(
                    onLoginSuccess = onAuthComplete,
                    onNavigateToRegister = { navController.navigate(REGISTER_ROUTE) },
                    onNavigateToForgotPassword = { navController.navigate(FORGOT_PASSWORD_ROUTE) },
                    onNavigateToTwoFactor = { tempToken ->
                        navController.navigate("two_factor/$tempToken")
                    },
                )
            }
        }
        composable(REGISTER_ROUTE) {
            ScreenTransitionWrapper(transition) {
                RegisterScreen(
                    onRegistered = { navController.popBackStack(LOGIN_ROUTE, inclusive = false) },
                    onNavigateToLogin = { navController.popBackStack() },
                )
            }
        }
        composable(FORGOT_PASSWORD_ROUTE) {
            ScreenTransitionWrapper(transition) {
                ForgotPasswordScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = TWO_FACTOR_ROUTE,
            arguments = listOf(navArgument("tempToken") { type = NavType.StringType }),
        ) {
            ScreenTransitionWrapper(transition) {
                TwoFactorScreen(
                    onVerified = onAuthComplete,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = VERIFY_EMAIL_ROUTE,
            arguments = listOf(navArgument("email") { type = NavType.StringType }),
        ) {
            ScreenTransitionWrapper(transition) {
                VerifyEmailScreen(
                    onVerified = { navController.popBackStack(LOGIN_ROUTE, inclusive = false) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(TERMS_ROUTE) {
            ScreenTransitionWrapper(transition) {
                TermsScreen(
                    onAccepted = onAuthComplete,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = RESET_PASSWORD_ROUTE,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("token") { type = NavType.StringType },
            ),
        ) {
            ScreenTransitionWrapper(transition) {
                ResetPasswordScreen(
                    onResetComplete = { navController.popBackStack(LOGIN_ROUTE, inclusive = false) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
