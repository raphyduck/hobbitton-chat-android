package com.librechat.android.core.ui.components

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
actual fun ScreenTransitionWrapper(
    transition: Transition<EnterExitState>,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val alpha by transition.animateFloat(
        transitionSpec = { tween(300, easing = LinearEasing) },
        label = "screenAlpha",
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> 0f
            EnterExitState.Visible -> 1f
            EnterExitState.PostExit -> 0f
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        content()
    }
}
