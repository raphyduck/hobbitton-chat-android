package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Eva's mark (19/10/2026): a speech bubble in the accent holding the serif « e » of Source Serif 4
 * semibold, in the page's colour. The launcher icon (`ic_launcher_foreground`, `:app`) draws the
 * same two paths; both live in a 100-unit box.
 */
@Composable
fun EvaMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    letterColor: Color = MaterialTheme.colorScheme.surface,
) {
    val bubble = remember { PathParser().parsePathString(BUBBLE).toPath() }
    val letter = remember { PathParser().parsePathString(LETTER).toPath() }
    Canvas(modifier) {
        val unit = minOf(size.width, size.height) / BOX
        scale(unit, pivot = Offset.Zero) {
            drawPath(bubble, color)
            drawPath(letter, letterColor)
        }
    }
}

private const val BOX = 100f

/** The bubble: a circle of radius 34 around (50, 46) united with its tail, pointing down-left. */
private const val BUBBLE = "M24.88 68.91L14 86L34.79 76.41A34 34 0 1 0 24.88 68.91Z"

/** « e », Source Serif 4 semibold at 60 units, centred on the bubble. */
private const val LETTER =
    "M51.02 34.23Q49.46 34.23 47.99 35.13Q46.52 36.03 45.5 38.13Q44.48 40.23 44.24 43.83" +
    "H52.34Q54.68 43.83 55.43 42.93Q56.18 42.03 56.18 40.41Q56.18 37.29 54.74 35.76" +
    "Q53.3 34.23 51.02 34.23Z" +
    "M51.32 61.71Q46.94 61.71 43.64 59.97Q40.34 58.23 38.51 54.9Q36.68 51.57 36.68 46.83" +
    "Q36.68 42.03 38.6 38.55Q40.52 35.07 43.79 33.18Q47.06 31.29 51.02 31.29" +
    "Q54.62 31.29 57.32 32.79Q60.02 34.29 61.52 36.99Q63.02 39.69 63.02 43.35" +
    "Q63.02 45.27 62.66 46.65H44.18Q44.42 51.75 47 54.36Q49.58 56.97 53.6 56.97" +
    "Q58.58 56.97 61.7 53.43L63.32 54.75Q61.46 58.05 58.7 59.88Q55.94 61.71 51.32 61.71Z"
