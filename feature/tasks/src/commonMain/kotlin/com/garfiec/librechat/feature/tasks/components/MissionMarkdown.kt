package com.garfiec.librechat.feature.tasks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.garfiec.librechat.core.ui.markdown.InlineSegment
import com.garfiec.librechat.core.ui.markdown.MarkdownSegment
import com.garfiec.librechat.core.ui.markdown.parseMarkdownSegments
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * A mission's prose, rendered exactly the way the chat renders its own.
 *
 * Prose goes through the same mikepenz renderer as the chat (pulled in directly, the
 * `feature/skills` precedent — features depend on `:core:*` only, never on each other) with the
 * chat's colour and typography mapping copied verbatim from `MarkdownContent.kt`. The previous
 * hand-rolled renderer looked close and *was not*: links rendered as literal `[label](url)`,
 * ordered lists and blockquotes came out raw, a hard-wrapped paragraph stayed hard-wrapped, and
 * `####` downward printed its own octothorpes. A second markdown grammar is a second place for
 * such gaps to hide; this file no longer owns one.
 *
 * Code blocks and tables still come from the shared segment parser rather than the library —
 * the same split the chat makes, because the library renders neither the way a transcript needs
 * (scrollable code that never wraps, columns that align).
 */
@Composable
internal fun MissionMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontScale: Float = 1f,
) {
    val segments = remember(text) { parseMarkdownSegments(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.CodeBlock -> MissionCodeBlock(segment.code, segment.language)
                is MarkdownSegment.Table -> MissionTable(segment)
                is MarkdownSegment.TextBlock -> Prose(segment.text, color, fontScale)
                // LaTeX has no renderer here: a mission answers in prose and tool output, and a
                // formula shown as its source beats a formula shown as nothing.
                is MarkdownSegment.LatexBlock -> Prose(segment.latex, color, fontScale)
                is MarkdownSegment.InlineLatexText ->
                    Prose(segment.segments.joinToString("") { it.rawText() }, color, fontScale)
            }
        }
    }
}

private fun InlineSegment.rawText(): String = when (this) {
    is InlineSegment.Text -> text
    is InlineSegment.Latex -> latex
}

/** The chat's markdown theme, verbatim — `MarkdownContent.kt` is the reference, not a suggestion. */
@Composable
private fun Prose(content: String, color: Color, fontScale: Float) {
    val bodyLarge = MaterialTheme.typography.bodyLarge
    val bodyMedium = MaterialTheme.typography.bodyMedium
    Markdown(
        content = content,
        colors = markdownColor(
            text = color,
            codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
            inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
            dividerColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.headlineLarge.scaled(fontScale),
            h2 = MaterialTheme.typography.headlineMedium.scaled(fontScale),
            h3 = MaterialTheme.typography.headlineSmall.scaled(fontScale),
            h4 = MaterialTheme.typography.titleLarge.scaled(fontScale),
            h5 = MaterialTheme.typography.titleMedium.scaled(fontScale),
            h6 = MaterialTheme.typography.titleSmall.scaled(fontScale),
            text = bodyLarge.scaled(fontScale),
            paragraph = bodyLarge.scaled(fontScale),
            quote = bodyLarge.copy(fontStyle = FontStyle.Italic).scaled(fontScale),
            code = bodyMedium.copy(fontFamily = FontFamily.Monospace).scaled(fontScale),
            inlineCode = bodyLarge.copy(fontFamily = FontFamily.Monospace).scaled(fontScale),
            ordered = bodyLarge.scaled(fontScale),
            bullet = bodyLarge.scaled(fontScale),
            list = bodyLarge.scaled(fontScale),
        ),
    )
}

private fun TextStyle.scaled(multiplier: Float): TextStyle =
    if (multiplier == 1f || !fontSize.isSpecified) this else copy(fontSize = fontSize * multiplier)

/** A fenced block: monospace on a raised surface, with its language named when the model gave one. */
@Composable
private fun MissionCodeBlock(code: String, language: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!language.isNullOrBlank()) {
            Text(
                language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            code.trimEnd(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            // Code does not wrap: a wrapped line reads as two statements. It scrolls instead, and
            // only the block scrolls — never the page.
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/**
 * Equal-weight columns, so the columns of one row line up with the columns of the next — the
 * previous free-spaced rows drifted apart and a table read as word soup.
 */
@Composable
private fun MissionTable(table: MarkdownSegment.Table) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            table.headers.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        table.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
            }
        }
    }
}
