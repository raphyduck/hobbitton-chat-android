package com.garfiec.librechat.core.ui.input

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Send, or stop what is running, in the same round spot at the end of the composer's row. Animated
 * across the swap: the two states occupy one place, and a hard cut reads as the button having been
 * replaced.
 *
 * Moved here from the mission conversation (D-076) so the next composer built on [ChatInputBox]
 * takes this one rather than a copy. The chat keeps its own `SendStopButton`: it also queues,
 * steers and updates a queued message, which a plain send/stop does not.
 *
 * The labels are the caller's, as [ChatInputPill]'s are — each feature already has « send » and
 * « stop » in its own strings, in its own words.
 */
@Composable
fun ComposerSendButton(
    running: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    sendContentDescription: String,
    stopContentDescription: String,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = running,
        modifier = modifier,
        transitionSpec = { (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut()) },
        label = "composer_send_stop_toggle",
    ) { showStop ->
        if (showStop) {
            IconButton(
                onClick = onStop,
                modifier = Modifier.size(ChatInputDefaults.controlSize),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(imageVector = Icons.Filled.Stop, contentDescription = stopContentDescription)
            }
        } else {
            IconButton(
                onClick = onSend,
                modifier = Modifier.size(ChatInputDefaults.controlSize),
                enabled = canSend,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(imageVector = Icons.Filled.ArrowUpward, contentDescription = sendContentDescription)
            }
        }
    }
}
