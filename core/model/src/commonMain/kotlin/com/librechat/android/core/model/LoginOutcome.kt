package com.librechat.android.core.model

sealed interface LoginOutcome {
    data class Success(val user: User) : LoginOutcome
    data class TwoFactorRequired(val tempToken: String) : LoginOutcome
}
