package com.garfiec.librechat.core.ui.components

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

private const val FALLBACK_CORNER_RADIUS_DP = 24f

/**
 * Wraps screen content to apply rounded corners during navigation transitions.
 * Pass the [transition] from AnimatedVisibilityScope inside a composable() block.
 */
@Composable
actual fun ScreenTransitionWrapper(
    transition: Transition<EnterExitState>,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val screenCornerRadiusDp = rememberScreenCornerRadiusDp()

    val cornerRadius by transition.animateFloat(
        transitionSpec = { tween(300, easing = LinearEasing) },
        label = "screenCorner",
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> screenCornerRadiusDp
            EnterExitState.Visible -> 0f
            EnterExitState.PostExit -> screenCornerRadiusDp
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius.dp)),
    ) {
        content()
    }
}

@Composable
private fun rememberScreenCornerRadiusDp(): Float {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val insets = view.rootWindowInsets
            val topLeft = insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
            if (topLeft != null && topLeft.radius > 0) {
                with(density) { topLeft.radius.toDp().value }
            } else {
                FALLBACK_CORNER_RADIUS_DP
            }
        } else {
            FALLBACK_CORNER_RADIUS_DP
        }
    }
}
