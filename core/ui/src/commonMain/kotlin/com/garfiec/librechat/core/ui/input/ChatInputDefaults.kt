package com.garfiec.librechat.core.ui.input

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The composer's look, in one place.
 *
 * Lives here rather than in a feature because two screens render a composer — the conversation and
 * the agent's question form in `feature/tasks` — and feature modules cannot see each other. A copy
 * in each is a copy that drifts: the tasks composer shipped on 29/08/2026 with a bare
 * `OutlinedTextField` and read as a different, lesser control on a screen that does the same thing.
 *
 * Since lot 2 (10/10/2026) the box is a raised card set on the page, as Claude's: white with a soft
 * shadow on the light page, a lighter brown without one on the dark page, a hairline either way.
 */
object ChatInputDefaults {
    val shape: Shape = RoundedCornerShape(24.dp)

    /** The raised box: the lightest surface on the light page, a lifted one on the dark page. */
    val containerColor: Color
        @Composable get() = if (isLightPage()) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }

    /** The hairline around the box, and around a question's option rows. */
    val borderColor: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant

    /** A shadow lifts a white box off ivory; on the dark page the lighter fill does that alone. */
    val shadowElevation: Dp
        @Composable get() = if (isLightPage()) 2.dp else 0.dp

    val keyboardOptions: KeyboardOptions = KeyboardOptions(
        imeAction = ImeAction.Default,
        capitalization = KeyboardCapitalization.Sentences,
    )

    /**
     * Every round control in the box's bottom row — « + », the mic, send. One size so the row reads
     * as one line of controls; 40 dp keeps each a thumb's target and the row one line high.
     */
    val controlSize: Dp = 40.dp

    @Composable
    private fun isLightPage(): Boolean = MaterialTheme.colorScheme.surface.luminance() > LIGHT_PAGE_LUMINANCE

    private const val LIGHT_PAGE_LUMINANCE = 0.5f
}
