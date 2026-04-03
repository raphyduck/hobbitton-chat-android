package com.librechat.android.core.ui.components

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Wraps screen content to apply rounded corners during navigation transitions.
 * On Android, reads the device screen corner radius for a polished effect.
 * On iOS, renders content directly (no transition animation needed).
 */
@Composable
expect fun ScreenTransitionWrapper(
    transition: Transition<EnterExitState>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
