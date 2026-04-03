package com.librechat.android.feature.chat.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

// --- Light mode highlight colors ---
private val SearchHighlightYellowLight = Color(0xFFFFEB3B)
private val SearchHighlightOrangeLight = Color(0xFFFF9800)

// --- Dark mode highlight colors (muted, for readable contrast with light text) ---
private val SearchHighlightYellowDark = Color(0xFF5C4800)
private val SearchHighlightOrangeDark = Color(0xFF804D00)

/**
 * Background color for all search matches (non-focused), adapted to theme.
 */
val SearchHighlightYellow: Color
    @Composable get() = if (isSystemInDarkTheme()) SearchHighlightYellowDark else SearchHighlightYellowLight

/**
 * Background color for the currently focused search match, adapted to theme.
 */
val SearchHighlightOrange: Color
    @Composable get() = if (isSystemInDarkTheme()) SearchHighlightOrangeDark else SearchHighlightOrangeLight

/**
 * Returns the non-focused search highlight color for the given theme.
 */
fun searchHighlightYellow(isDarkTheme: Boolean): Color =
    if (isDarkTheme) SearchHighlightYellowDark else SearchHighlightYellowLight

/**
 * Returns the focused search highlight color for the given theme.
 */
fun searchHighlightOrange(isDarkTheme: Boolean): Color =
    if (isDarkTheme) SearchHighlightOrangeDark else SearchHighlightOrangeLight

/**
 * Builds an [AnnotatedString] from [text] with background spans applied to all
 * case-insensitive occurrences of [query].
 *
 * All matches get a yellow background. If [focusedOccurrence] >= 0,
 * that specific occurrence (0-based within this text) gets an orange background
 * instead to indicate the currently focused search result.
 *
 * @param isDarkTheme When true, uses muted dark-mode highlight colors that maintain
 *   contrast with light text. When false, uses bright highlight colors for light mode.
 */
fun buildHighlightedString(
    text: String,
    query: String,
    focusedOccurrence: Int = -1,
    isDarkTheme: Boolean = false,
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)

    val highlightColor = searchHighlightYellow(isDarkTheme)
    val focusedColor = searchHighlightOrange(isDarkTheme)

    return buildAnnotatedString {
        append(text)

        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var startIndex = 0
        var occurrence = 0

        while (true) {
            val foundIndex = lowerText.indexOf(lowerQuery, startIndex)
            if (foundIndex < 0) break

            val bgColor = if (occurrence == focusedOccurrence) {
                focusedColor
            } else {
                highlightColor
            }

            addStyle(
                SpanStyle(background = bgColor),
                start = foundIndex,
                end = foundIndex + query.length,
            )

            occurrence++
            startIndex = foundIndex + query.length
        }
    }
}

/**
 * Counts the number of case-insensitive occurrences of [query] in [text].
 */
fun countOccurrences(text: String, query: String): Int {
    if (query.isBlank() || text.isBlank()) return 0
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    var count = 0
    var startIndex = 0
    while (true) {
        val foundIndex = lowerText.indexOf(lowerQuery, startIndex)
        if (foundIndex < 0) break
        count++
        startIndex = foundIndex + query.length
    }
    return count
}
