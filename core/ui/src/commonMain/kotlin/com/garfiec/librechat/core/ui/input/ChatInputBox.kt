package com.garfiec.librechat.core.ui.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The composer, laid out as Claude's: one raised box, the text on top, and under it a single row of
 * controls — « + », the model and the connectors as plain labels, then the mic and send at the end.
 *
 * Shared by the conversation and the question form, which cannot see each other's code. The box
 * owns the frame; what goes in it — the field ([ChatInputField]) and the row — is the caller's.
 */
@Composable
fun ChatInputBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ChatInputDefaults.shape,
        color = ChatInputDefaults.containerColor,
        border = BorderStroke(1.dp, ChatInputDefaults.borderColor),
        shadowElevation = ChatInputDefaults.shadowElevation,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/**
 * The text inside [ChatInputBox]: a bare field with the box's own margins, no fill and no
 * underline, so the text sits where the eye expects it and the box alone draws the edge. Material's
 * `TextField` carried 16 dp of its own padding and an underline the box then had to hide.
 */
@Composable
fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLines: Int = DEFAULT_MAX_LINES,
    enabled: Boolean = true,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = ChatInputDefaults.keyboardOptions,
        maxLines = maxLines,
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}

/**
 * A control in the composer's row that carries its current value as a plain label with a chevron —
 * the model's name, « 3 connecteurs » — because what the next message will run with should not take
 * a sheet to find out. No fill: the box is the frame, and a filled pill inside it read as a button
 * fighting the field for attention.
 */
@Composable
fun ChatInputPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        // The row lives in a horizontally scrolling parent, whose width is unbounded: measured at
        // its own intrinsic width (capped by the caller's `widthIn`), the label keeps its space and
        // the chevron its place. Without this, the weighted label measured to nothing.
        modifier = modifier
            .width(IntrinsicSize.Max)
            .clip(PillShape)
            .clickable(onClick = onClick)
            .heightIn(min = PILL_MIN_HEIGHT)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        leadingIcon?.invoke()
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val DEFAULT_MAX_LINES = 6
private val PILL_MIN_HEIGHT = 32.dp
private val PillShape = RoundedCornerShape(10.dp)
