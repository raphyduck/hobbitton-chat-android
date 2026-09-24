package com.garfiec.librechat.core.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The composer, laid out as Claude's (capture of 24/09/2026): one rounded box, the text on top,
 * and under it a single row of controls — « + », the pills, then the mic and send at the far end.
 *
 * Shared by the chat and a mission's conversation, which cannot see each other's code. The box
 * owns the frame; what goes in it — the field ([ChatInputDefaults.embeddedTextFieldColors]) and the
 * row — is the caller's.
 */
@Composable
fun ChatInputBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ChatInputDefaults.shape)
            .background(ChatInputDefaults.containerColor)
            .border(1.dp, ChatInputDefaults.borderColor, ChatInputDefaults.shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/**
 * A rounded, filled pill in the composer's row — the look of Claude's model button. Carries its
 * current value as its label (the model's name, « 3 connecteurs »), because what the next message
 * will run with should not take a sheet to find out.
 */
@Composable
fun ChatInputPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = ChatInputDefaults.controlSize),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leadingIcon?.invoke()
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
