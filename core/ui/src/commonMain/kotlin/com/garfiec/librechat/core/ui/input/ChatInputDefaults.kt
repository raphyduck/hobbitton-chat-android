package com.garfiec.librechat.core.ui.input

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The composer's look, in one place.
 *
 * Lives here rather than in `feature/chat` because two screens now render a composer — the chat and a
 * mission session's conversation in `feature/tasks` — and feature modules cannot see each other. A
 * copy in each is a copy that drifts: the tasks composer shipped on 29/08/2026 with a bare
 * `OutlinedTextField` and read as a different, lesser control on a screen that does the same thing.
 */
object ChatInputDefaults {
    val shape: Shape = RoundedCornerShape(24.dp)

    /** Resting fill shared by the composer input box and the floating top-bar chips. */
    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

    /** Resting border shared by the composer input box and the floating top-bar chips. */
    val borderColor: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant

    val keyboardOptions: KeyboardOptions = KeyboardOptions(
        imeAction = ImeAction.Default,
        capitalization = KeyboardCapitalization.Sentences,
    )

    /**
     * Every round control in the box's bottom row — « + », pills, mic, send. One size so the row
     * reads as one line of controls; 44 dp keeps each a thumb's target.
     */
    val controlSize: Dp = 44.dp

    /**
     * The text field inside [ChatInputBox]. The box draws the frame; the field draws nothing of its
     * own — no fill, no underline — so the two cannot disagree about where the edge is.
     */
    @Composable
    fun embeddedTextFieldColors(): TextFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )
}
