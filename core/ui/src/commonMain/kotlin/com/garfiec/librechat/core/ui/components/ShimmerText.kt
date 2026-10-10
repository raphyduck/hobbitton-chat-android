package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle

/**
 * A line that shimmers while the agent works, as Claude's « Thinking… » does: a band of the full
 * text colour slides across the quiet one, so the words read as alive without a spinner beside
 * them. One small text per turn, so the per-frame recomposition stays cheap.
 */
@Composable
fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val bright = MaterialTheme.colorScheme.onSurface
    var width by remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "shimmer_text")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_PERIOD_MS, easing = LinearEasing)),
        label = "shimmer_shift",
    )
    // The band is as wide as the text and travels twice its width, so it enters from the left and
    // leaves on the right before starting over.
    val start = -width + shift * 2f * width
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            brush = Brush.linearGradient(
                colors = listOf(quiet, bright, quiet),
                start = Offset(start, 0f),
                end = Offset(start + width.coerceAtLeast(1f), 0f),
            ),
        ),
        onTextLayout = { width = it.size.width.toFloat() },
    )
}

private const val SHIMMER_PERIOD_MS = 1_800
