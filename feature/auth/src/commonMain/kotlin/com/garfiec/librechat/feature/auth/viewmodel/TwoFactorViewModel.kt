package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.model.VerifyTwoFactorOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class TwoFactorUiState(
    /**
     * The whole code, as ONE string — not six.
     *
     * It used to be six independent boxes, which made the screen structurally impossible to paste
     * into: each box kept a single character, so a pasted six-digit code lost five of them. The
     * entry is one field behind six drawn boxes, and this is its value.
     */
    val code: String = "",
    val isBackupMode: Boolean = false,
    val backupCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isVerified: Boolean = false,
    // Bumped whenever the entered code is cleared as spent (evaluated-and-rejected, or accepted
    // with an unusable session); the digit row keys its focus-reset effect on this.
    val codeAttempt: Int = 0,
)

private const val CODE_LENGTH = 6

class TwoFactorViewModel(
    private val authRepository: AuthRepository,
    initialTempToken: String? = null,
) : ViewModel() {

    private val tempToken: String = initialTempToken ?: ""

    private val _uiState = MutableStateFlow(TwoFactorUiState())
    val uiState: StateFlow<TwoFactorUiState> = _uiState.asStateFlow()

    // Bumped by toggleBackupMode() to supersede any in-flight verify. Superseding only ever
    // suppresses a stale verify's FAILURE (it must not repaint the freshly-switched screen);
    // a Success is always honored, because it reflects a session already committed in the
    // token store — the store, not this coroutine, is the source of truth for sign-in, and
    // suppressing it would strand an authenticated user on this screen.
    private var submitGeneration = 0

    fun onCodeChanged(value: String) {
        // The field sanitizes to digits and caps the length, so nothing is re-validated here.
        if (value == _uiState.value.code) return
        _uiState.value = _uiState.value.copy(code = value, error = null)

        // Auto-submit on the complete code — typed in, or pasted in one action. Guarded on
        // `isLoading` because composition can deliver the terminal value more than once, and the
        // code is single-use: a second submit spends it and the first attempt fails on a code the
        // server has already consumed.
        if (value.length == CODE_LENGTH && !_uiState.value.isLoading) {
            submit()
        }
    }

    fun onBackupCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(backupCode = code, error = null)
    }

    fun toggleBackupMode() {
        // Supersede any in-flight verify so its eventual failure can't repaint this freshly-
        // switched screen. The verify itself keeps running — a late Success still completes
        // sign-in — and the toggle stays responsive even while a verify is stalled, keeping
        // the backup-code fallback reachable.
        submitGeneration++
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isBackupMode = !_uiState.value.isBackupMode,
            error = null,
            code = "",
            backupCode = "",
        )
    }

    fun submit() {
        val state = _uiState.value
        val code = if (state.isBackupMode) {
            state.backupCode.trim()
        } else {
            state.code
        }

        if (code.isBlank()) return

        val generation = submitGeneration
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val outcome = authRepository.verifyTwoFactor(tempToken, code, isBackupCode = state.isBackupMode)) {
                // Always honored, even when superseded by a mode toggle: the session is already
                // committed in the token store, so anything but completing sign-in would strand
                // an authenticated user here.
                is VerifyTwoFactorOutcome.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isVerified = true,
                    )
                }
                // Failure outcomes are dropped when superseded — the toggle already reset the
                // entry, and a stale error must not repaint the switched screen. Whether the entry
                // clears or is kept follows the outcome's contract: the server consumed the
                // single-use code (rejected it, or accepted it without a usable session) → clear for
                // a fresh one; it never evaluated the code → keep it for a plain retry. Focus resets
                // (via codeAttempt) precisely when the boxes are cleared.
                is VerifyTwoFactorOutcome.CodeConsumed -> applyVerifyFailure(generation, outcome.message, clearEntry = true)
                is VerifyTwoFactorOutcome.NotEvaluated -> applyVerifyFailure(generation, outcome.message, clearEntry = false)
                is VerifyTwoFactorOutcome.ConnectionFailure -> applyVerifyFailure(
                    generation,
                    "Couldn't reach the server. Check your connection and try again.",
                    clearEntry = false,
                )
            }
        }
    }

    /**
     * Applies a superseded-aware verify failure. When [clearEntry] the server consumed the entered
     * code, so the boxes clear for a fresh one and [TwoFactorUiState.codeAttempt] bumps to reset
     * focus; otherwise the code was never evaluated and stays intact for a plain retry. The
     * supersession guard lives here alone: a failure whose generation was superseded by a mode
     * toggle is dropped so it can't repaint the freshly-switched screen.
     */
    private fun applyVerifyFailure(generation: Int, message: String, clearEntry: Boolean) {
        if (generation != submitGeneration) return
        val state = _uiState.value
        _uiState.value = state.copy(
            isLoading = false,
            error = message,
            code = if (clearEntry) "" else state.code,
            backupCode = if (clearEntry) "" else state.backupCode,
            codeAttempt = if (clearEntry) state.codeAttempt + 1 else state.codeAttempt,
        )
    }
}
