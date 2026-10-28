package com.garfiec.librechat.shared.engine

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

@Composable
internal actual fun rememberStoresKeptOnDispose(): () -> Boolean {
    val activity by rememberUpdatedState(LocalActivity.current)
    return { activity?.isChangingConfigurations == true }
}
