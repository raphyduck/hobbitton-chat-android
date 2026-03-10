package com.librechat.android.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Displays a message timestamp with relative time ("2m ago") that toggles to absolute on tap. Parses multiple ISO 8601 variants; returns empty on invalid input. */
@Composable
fun MessageTimestamp(
    isoTimestamp: String,
    modifier: Modifier = Modifier,
) {
    var showAbsolute by remember { mutableStateOf(false) }

    val timeText = remember(isoTimestamp, showAbsolute) {
        if (showAbsolute) {
            formatAbsoluteTimestamp(isoTimestamp)
        } else {
            formatRelativeTimestamp(isoTimestamp)
        }
    }

    if (timeText.isNotEmpty()) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                showAbsolute = !showAbsolute
            },
        )
    }
}

internal fun formatRelativeTimestamp(isoTimestamp: String): String {
    val date = parseTimestamp(isoTimestamp) ?: return ""
    val now = System.currentTimeMillis()
    val diff = now - date.time
    if (diff < 0) return "now"

    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        seconds < 60 -> "now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        else -> formatAbsoluteTimestamp(isoTimestamp)
    }
}

internal fun formatAbsoluteTimestamp(isoTimestamp: String): String {
    val date = parseTimestamp(isoTimestamp) ?: return ""
    val format = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    return format.format(date)
}

private fun parseTimestamp(timestamp: String): Date? {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )
    for (pattern in formats) {
        try {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.parse(timestamp)
        } catch (_: Exception) {
            continue
        }
    }
    return null
}
