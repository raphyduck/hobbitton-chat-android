package com.garfiec.librechat.feature.conversations.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.extensions.toInstantOrNull
import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.ui.components.endpointIconPainter
import com.garfiec.librechat.core.ui.components.isMonochromeIcon
import com.garfiec.librechat.feature.conversations.resources.*
import com.garfiec.librechat.feature.conversations.resources.Res
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun ConversationItem(
    data: ConversationDisplayData,
    onClick: () -> Unit,
    onActionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val relativeTime = remember(data.updatedAt) {
        data.updatedAt?.toInstantOrNull()?.toRelativeTimeString() ?: ""
    }

    val endpointLabel = remember(data.endpoint) {
        data.endpoint?.toDisplayLabel() ?: "Chat"
    }

    val iconPainter = data.endpoint?.let { endpointIconPainter(it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconPainter != null) {
            val isMonochrome = data.endpoint?.isMonochromeIcon() == true
            Icon(
                painter = iconPainter,
                contentDescription = endpointLabel,
                modifier = Modifier.size(24.dp),
                tint = if (isMonochrome) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color.Unspecified
                },
            )
        } else {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = endpointLabel,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = endpointLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val modelName = data.model
                if (modelName != null) {
                    Text(
                        text = " \u00B7 ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                if (relativeTime.isNotEmpty()) {
                    Text(
                        text = " \u00B7 ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        if (data.isBookmarked) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = stringResource(Res.string.cd_bookmarked),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(onClick = onActionsClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.cd_conversation_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Instant.toRelativeTimeString(): String {
    val now = Clock.System.now()
    val duration = now - this
    val minutes = duration.inWholeMinutes
    val hours = duration.inWholeHours
    val days = duration.inWholeDays

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val date = toLocalDateTime(TimeZone.currentSystemDefault()).date
            val monthAbbr = when (date.monthNumber) {
                1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"
                5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
                9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
                else -> ""
            }
            "$monthAbbr ${date.dayOfMonth}"
        }
    }
}

private fun EModelEndpoint.toDisplayLabel(): String = when (this) {
    EModelEndpoint.OPENAI -> "OpenAI"
    EModelEndpoint.AZURE_OPENAI -> "Azure"
    EModelEndpoint.GOOGLE -> "Google"
    EModelEndpoint.ANTHROPIC -> "Anthropic"
    EModelEndpoint.ASSISTANTS -> "Assistants"
    EModelEndpoint.AZURE_ASSISTANTS -> "Azure Assistants"
    EModelEndpoint.AGENTS -> "Agents"
    EModelEndpoint.CUSTOM -> "Custom"
    EModelEndpoint.BEDROCK -> "Bedrock"
}
