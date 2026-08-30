package com.garfiec.librechat.core.ui.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography

/**
 * How rendered markdown looks, everywhere it is rendered.
 *
 * The same thirteen typography slots and four colour roles used to be written out three times —
 * the chat's Android renderer, the chat's iOS renderer, and the Tasks conversation — and the three
 * copies had already begun to disagree: the Tasks copy scaled glyphs without scaling line height,
 * so at the SMALL and LARGE settings its paragraphs kept MEDIUM's line spacing and read visibly
 * tighter than the same text in a chat. A theme that lives in one place cannot drift from itself.
 *
 * This is the theme only — the colours and the type scale. What surrounds it stays where it is:
 * the AST cache, the streaming cursor, syntax highlighting and the table renderer are the chat's,
 * and the Tasks conversation deliberately does without them.
 */
@Composable
fun chatMarkdownColors(
    /**
     * Overridden where the prose does not sit on the surface — the Tasks user bubble renders on
     * `secondaryContainer` and needs its `on` colour, which is the one thing about the theme that
     * legitimately varies by caller.
     */
    text: Color = MaterialTheme.colorScheme.onSurface,
): MarkdownColors = markdownColor(
    text = text,
    codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
    inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
    dividerColor = MaterialTheme.colorScheme.outlineVariant,
)

/** The type scale, with the reader's font-size setting already applied to every slot. */
@Composable
fun chatMarkdownTypography(fontSizeMultiplier: Float = 1f): MarkdownTypography {
    val bodyLarge = MaterialTheme.typography.bodyLarge
    val bodyMedium = MaterialTheme.typography.bodyMedium
    return markdownTypography(
        h1 = MaterialTheme.typography.headlineLarge.scaleFontSize(fontSizeMultiplier),
        h2 = MaterialTheme.typography.headlineMedium.scaleFontSize(fontSizeMultiplier),
        h3 = MaterialTheme.typography.headlineSmall.scaleFontSize(fontSizeMultiplier),
        h4 = MaterialTheme.typography.titleLarge.scaleFontSize(fontSizeMultiplier),
        h5 = MaterialTheme.typography.titleMedium.scaleFontSize(fontSizeMultiplier),
        h6 = MaterialTheme.typography.titleSmall.scaleFontSize(fontSizeMultiplier),
        text = bodyLarge.scaleFontSize(fontSizeMultiplier),
        paragraph = bodyLarge.scaleFontSize(fontSizeMultiplier),
        quote = bodyLarge.copy(fontStyle = FontStyle.Italic).scaleFontSize(fontSizeMultiplier),
        code = bodyMedium.copy(fontFamily = FontFamily.Monospace).scaleFontSize(fontSizeMultiplier),
        inlineCode = bodyLarge.copy(fontFamily = FontFamily.Monospace).scaleFontSize(fontSizeMultiplier),
        ordered = bodyLarge.scaleFontSize(fontSizeMultiplier),
        bullet = bodyLarge.scaleFontSize(fontSizeMultiplier),
        list = bodyLarge.scaleFontSize(fontSizeMultiplier),
    )
}

/**
 * Scales a style's font size **and its line height** by [multiplier].
 *
 * Both, always: scaling glyphs alone leaves the lines at the unscaled spacing, which reads as
 * cramped at LARGE and as airy at SMALL — a bug that shipped in the Tasks tab for exactly one day.
 * Each unit is checked for `isSpecified` on its own, because a style may specify one and not the
 * other, and multiplying `TextUnit.Unspecified` throws.
 */
fun TextStyle.scaleFontSize(multiplier: Float): TextStyle {
    if (multiplier == 1f) return this
    return copy(
        fontSize = if (fontSize.isSpecified) (fontSize.value * multiplier).sp else fontSize,
        lineHeight = if (lineHeight.isSpecified) (lineHeight.value * multiplier).sp else lineHeight,
    )
}
