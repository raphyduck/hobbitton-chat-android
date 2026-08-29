package com.garfiec.librechat.core.ui.input

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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

    @Composable
    fun textFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = containerColor,
        unfocusedContainerColor = containerColor,
        focusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedBorderColor = borderColor,
    )
}
