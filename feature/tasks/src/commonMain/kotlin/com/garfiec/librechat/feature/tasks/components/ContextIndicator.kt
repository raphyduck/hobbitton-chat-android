package com.garfiec.librechat.feature.tasks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_context
import com.garfiec.librechat.feature.tasks.resources.tasks_context_count
import com.garfiec.librechat.feature.tasks.resources.tasks_context_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_context_model
import com.garfiec.librechat.feature.tasks.resources.tasks_context_of
import com.garfiec.librechat.feature.tasks.resources.tasks_context_percent
import com.garfiec.librechat.feature.tasks.resources.tasks_context_unknown_window
import com.garfiec.librechat.feature.tasks.resources.tasks_context_window
import com.garfiec.librechat.feature.tasks.util.ContextUsage
import com.garfiec.librechat.feature.tasks.util.compactTokens
import org.jetbrains.compose.resources.stringResource

/**
 * The session's context as a small ring in the top bar (24/10/2026, as in Claude's app): the share
 * of the model's window the conversation fills, in the error colour past 80 %. Without a known
 * window the ring stays empty; the sheet gives the count. A tap opens [ContextSheet].
 */
@Composable
internal fun ContextRing(usage: ContextUsage, onClick: () -> Unit) {
    val description = usage.percent?.let { stringResource(Res.string.tasks_context_percent, it) }
        ?: stringResource(Res.string.tasks_context_count, compactTokens(usage.used))
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        CircularProgressIndicator(
            progress = { usage.fraction ?: 0f },
            modifier = Modifier.size(RING_SIZE),
            color = usage.tint(),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeWidth = RING_STROKE,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    }
}

/** What the ring stands for, spelled out: the share of the window, the count, the model it is measured against. */
@Composable
internal fun ContextSheet(usage: ContextUsage, modelLabel: String?, onDismiss: () -> Unit) {
    TasksBottomSheet(onDismiss = onDismiss, horizontalPadding = 24.dp, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.tasks_context), style = MaterialTheme.typography.titleMedium)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.tasks_context_window),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    usage.percent?.let { percent ->
                        Text(
                            stringResource(Res.string.tasks_context_percent, percent),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { usage.fraction ?: 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = usage.tint(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    usage.window?.let { window ->
                        stringResource(Res.string.tasks_context_of, compactTokens(usage.used), compactTokens(window))
                    } ?: stringResource(Res.string.tasks_context_unknown_window, compactTokens(usage.used)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        modelLabel?.let {
            Text(
                stringResource(Res.string.tasks_context_model, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(Res.string.tasks_context_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContextUsage.tint() =
    if (nearlyFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

private val RING_SIZE = 20.dp
private val RING_STROKE = 2.5.dp
