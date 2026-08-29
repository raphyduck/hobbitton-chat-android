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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.markdown.InlineSegment
import com.garfiec.librechat.core.ui.markdown.MarkdownSegment
import com.garfiec.librechat.core.ui.markdown.parseMarkdownSegments

/**
 * A mission's prose, rendered as markdown.
 *
 * Built on `:core:ui`'s **shared** parser — the same `parseMarkdownSegments` the chat renders from,
 * moved there in this change so its rules (fenced blocks of any length, tables, LaTeX, the
 * artifact-aware fence handling) live once rather than twice. The chat's own renderer stays in
 * `feature/chat`: it is layered with in-conversation search, citations and steer markers, which are
 * built on chat message semantics a mission session does not have. Sharing the parser is what is
 * genuinely common; sharing the renderer would mean dragging that machinery into a module with no
 * data for it.
 *
 * Inline emphasis is handled here rather than in the parser because the parser deliberately stops at
 * block level — the chat resolves inline spans through its own annotator.
 */
@Composable
fun MissionMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val segments = remember(text) { parseMarkdownSegments(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.CodeBlock -> MissionCodeBlock(segment.code, segment.language)
                is MarkdownSegment.Table -> MissionTable(segment)
                is MarkdownSegment.TextBlock -> MissionProse(segment.text, color)
                // LaTeX has no renderer here: a mission answers in prose and tool output, and a
                // formula shown as its source beats a formula shown as nothing.
                is MarkdownSegment.LatexBlock -> MissionProse(segment.latex, color)
                is MarkdownSegment.InlineLatexText ->
                    MissionProse(segment.segments.joinToString("") { it.rawText() }, color)
            }
        }
    }
}

private fun InlineSegment.rawText(): String = when (this) {
    is InlineSegment.Text -> text
    is InlineSegment.Latex -> latex
}

/** Paragraphs, headings and lists — the shapes a mission's answer actually uses. */
@Composable
private fun MissionProse(text: String, color: Color) {
    val lines = text.trim().lines()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.isBlank() -> Unit
                trimmed.startsWith("### ") -> Text(
                    inlineMarkdown(trimmed.removePrefix("### ")),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                )
                trimmed.startsWith("## ") -> Text(
                    inlineMarkdown(trimmed.removePrefix("## ")),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
                trimmed.startsWith("# ") -> Text(
                    inlineMarkdown(trimmed.removePrefix("# ")),
                    style = MaterialTheme.typography.titleLarge,
                    color = color,
                )
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> BulletLine(
                    inlineMarkdown(trimmed.drop(2)),
                    color,
                )
                else -> Text(inlineMarkdown(trimmed), style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
    }
}

@Composable
private fun BulletLine(text: AnnotatedString, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/**
 * Bold, italics and inline code. A deliberately small grammar: it covers what a model writes in a
 * sentence, and anything richer arrives as a block the parser already separated out.
 */
internal fun inlineMarkdown(source: String): AnnotatedString = buildAnnotatedString {
    var rest = source
    while (rest.isNotEmpty()) {
        val match = INLINE_MARKUP.find(rest)
        if (match == null) {
            append(rest)
            return@buildAnnotatedString
        }
        append(rest.substring(0, match.range.first))
        val bold = match.groups[1]?.value
        val italic = match.groups[2]?.value
        val code = match.groups[3]?.value
        when {
            bold != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            italic != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
            code != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
        }
        rest = rest.substring(match.range.last + 1)
    }
}

private val INLINE_MARKUP = Regex("""\*\*(.+?)\*\*|(?<!\*)\*(?!\*)([^*]+?)\*(?!\*)|`([^`]+?)`""")

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

@Composable
private fun MissionTable(table: MarkdownSegment.Table) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            table.headers.forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        table.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
