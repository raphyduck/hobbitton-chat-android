package com.librechat.android.feature.auth.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.datastore.ServerDataStore
import com.librechat.android.core.data.repository.AuthRepository
import com.librechat.android.core.data.repository.ConfigRepository
import com.librechat.android.core.model.LoginOutcome
import com.librechat.android.feature.auth.oauth.OAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val twoFactorTempToken: String? = null,
    val registrationEnabled: Boolean = false,
    val socialLoginEnabled: Boolean = false,
    val socialLogins: List<String> = emptyList(),
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val configRepository: ConfigRepository,
    private val oAuthManager: OAuthManager,
    private val serverDataStore: ServerDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            configRepository.startupConfig.collect { config ->
                if (config != null) {
                    _uiState.value = _uiState.value.copy(
                        registrationEnabled = config.registrationEnabled,
                        socialLoginEnabled = config.socialLoginEnabled,
                        socialLogins = config.socialLogins.orEmpty(),
                    )
                }
            }
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Please enter email and password")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = authRepository.login(state.email, state.password)) {
                is Result.Success -> {
                    when (val outcome = result.data) {
                        is LoginOutcome.Success -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isLoggedIn = true,
                            )
                        }
                        is LoginOutcome.TwoFactorRequired -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                twoFactorTempToken = outcome.tempToken,
                            )
                        }
                    }
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "Login failed",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun launchOAuth(context: Context, provider: String) {
        oAuthManager.launchOAuth(context, provider)
    }

    fun checkOAuthResult() {
        val serverUrl = serverDataStore.getBaseUrl()
        if (serverUrl.isBlank()) return

        val refreshToken = oAuthManager.extractTokenFromCookies(serverUrl) ?: return

        // Clear the cookie immediately to avoid re-reading on next onResume
        oAuthManager.clearOAuthCookie(serverUrl)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = authRepository.loginWithOAuthToken(refreshToken)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "OAuth login failed",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}
