package com.garfiec.librechat.shared.engine

import com.garfiec.librechat.core.model.scheduler.EXCERPT_MARK_END
import com.garfiec.librechat.core.model.scheduler.EXCERPT_MARK_START

/**
 * The scheduler's excerpt cut at its marks (14/11/2026): each piece of text, and whether it is a
 * word the search found. A mark left open runs to the end; the marks themselves are dropped.
 */
internal fun excerptSpans(excerpt: String): List<Pair<String, Boolean>> {
    val spans = mutableListOf<Pair<String, Boolean>>()
    var rest = excerpt
    while (rest.isNotEmpty()) {
        val start = rest.indexOf(EXCERPT_MARK_START)
        if (start < 0) {
            spans += rest to false
            break
        }
        if (start > 0) spans += rest.substring(0, start) to false
        val afterStart = rest.substring(start + EXCERPT_MARK_START.length)
        val end = afterStart.indexOf(EXCERPT_MARK_END)
        val found = if (end < 0) afterStart else afterStart.substring(0, end)
        if (found.isNotEmpty()) spans += found to true
        rest = if (end < 0) "" else afterStart.substring(end + EXCERPT_MARK_END.length)
    }
    return spans
}
