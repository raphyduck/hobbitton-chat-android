package com.garfiec.librechat.feature.tasks.util

/**
 * What a reply sounds like (28/10/2026, voice mode step 4, the phone's mouth): its prose without
 * the markdown that a voice would spell out. Code blocks are dropped, an image is its description,
 * a link its text, a table its cells in a row; headings, emphasis and list markers go. Pure, and
 * pinned by `SpeechTextTest`.
 */
fun speechText(markdown: String): String {
    var text = markdown.replace("\r\n", "\n")
    text = FENCED_CODE.replace(text, "\n")
    text = HTML_TAG.replace(text, "")
    text = IMAGE.replace(text) { it.groupValues[1] }
    text = LINK.replace(text) { it.groupValues[1] }
    text = BARE_URL.replace(text, "")
    text = INLINE_CODE.replace(text) { it.groupValues[1] }
    val lines = text.lines().mapNotNull(::spokenLine)
    return lines.joinToString("\n").replace(BLANK_RUN, "\n\n").trim()
}

private fun spokenLine(raw: String): String? {
    var line = raw.trim()
    if (TABLE_RULE.matches(line)) return null
    if (line.startsWith("|")) {
        line = line.trim('|').split('|').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
    }
    line = LINE_MARKERS.replace(line, "")
    line = EMPHASIS.replace(line, "")
    line = UNDERSCORE_EMPHASIS.replace(line, "")
    return line.trim()
}

private val FENCED_CODE = Regex("```[\\s\\S]*?(```|$)")
private val HTML_TAG = Regex("<[^>\\n]{1,80}>")
private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
private val BARE_URL = Regex("""https?://\S+""")
private val INLINE_CODE = Regex("`([^`\\n]*)`")

private val TABLE_RULE = Regex("""^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$""")

/** Headings, blockquotes and bullets at the start of a line; a numbered item keeps its number. */
private val LINE_MARKERS = Regex("""^(#{1,6}\s+|>\s?|[-*+]\s+)+""")
private val EMPHASIS = Regex("""\*{1,3}|~~""")
private val UNDERSCORE_EMPHASIS = Regex("""(?<!\w)_{1,2}|_{1,2}(?!\w)""")
private val BLANK_RUN = Regex("\n{3,}")
