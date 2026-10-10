package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.input.ChatInputDefaults

/**
 * A group of rows on a raised card, as the settings and the Tasks tab lay them out since lot 4
 * (10/10/2026): the composer's own surface and hairline ([ChatInputDefaults]), so a list and the
 * box one types into read as the same material. Rows inside are bare; [SectionDivider] separates
 * them, [SectionLabel] names the group above it.
 */
object SectionGroupDefaults {
    val shape: Shape = RoundedCornerShape(18.dp)
}

@Composable
fun SectionGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SectionGroupDefaults.shape,
        color = ChatInputDefaults.containerColor,
        border = BorderStroke(1.dp, ChatInputDefaults.borderColor),
    ) {
        Column(content = content)
    }
}

/** The hairline between two rows of a [SectionGroup], indented like the rows' text. */
@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** The quiet name above a [SectionGroup]. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}
