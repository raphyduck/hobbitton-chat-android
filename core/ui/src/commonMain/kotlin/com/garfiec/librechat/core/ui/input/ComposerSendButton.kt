package com.garfiec.librechat.core.ui.input

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp

/**
 * Send, or stop what is running, in the same round spot at the end of the composer's row.
 *
 * As on Claude's composer, the spot is empty until there is something to send: the brick circle
 * appears when text or a file is staged, and turns into a dark stop square while the answer runs.
 * Both swaps are animated — the states occupy one place, and a hard cut reads as the button having
 * been replaced.
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
    AnimatedVisibility(
        visible = running || canSend,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        label = "composer_send_visible",
    ) {
        AnimatedContent(
            targetState = running,
            transitionSpec = { (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut()) },
            label = "composer_send_stop_toggle",
        ) { showStop ->
            if (showStop) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(ChatInputDefaults.controlSize),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = stopContentDescription, modifier = Modifier.size(18.dp))
                }
            } else {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier.size(ChatInputDefaults.controlSize),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = sendContentDescription, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
