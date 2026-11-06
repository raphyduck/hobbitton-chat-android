package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EnginePromptPart

/**
 * What a message carries once its attachments are settled (06/11/2026): the words, with one line
 * per file deposited on the server, and the parts that ride in the message (photos, a deposited
 * video's first frame).
 */
class OutgoingAttachments(val text: String, val files: List<EnginePromptPart>)

/**
 * The line that names a deposited file in the message: `🎬 fuite.mp4 · 48 Mo · /srv/filemcp/…`
 * (`📎` for anything but a video). The model reads the path and hands it to the tools as it is;
 * the thread shows what left. Language-neutral on purpose, as the audio note's `🎙️` line is.
 */
fun depositedLine(filename: String?, mime: String, sizeBytes: Long, path: String): String {
    val icon = if (mime.startsWith("video/")) "🎬" else "📎"
    val name = filename?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/')
    return "$icon $name · ${humanSize(sizeBytes)} · $path"
}

/** The words, then the deposited files' lines, each on its own line after a blank one. */
fun withDepositedLines(text: String, lines: List<String>): String =
    (listOf(text.trim()) + lines).filter { it.isNotEmpty() }.joinToString("\n\n")

/** `48 Mo`, `850 ko`, `12 o`: the size as a person reads it, in the platform's French units. */
fun humanSize(bytes: Long): String = when {
    bytes >= MEGABYTE -> format(bytes / MEGABYTE.toDouble()) + " Mo"
    bytes >= KILOBYTE -> format(bytes / KILOBYTE.toDouble()) + " ko"
    else -> "$bytes o"
}

/** One decimal under ten, none above: `1,5 Mo`, `48 Mo`. */
private fun format(value: Double): String {
    val tenths = (value * 10).toLong()
    return if (value < 10 && tenths % 10 != 0L) "${tenths / 10},${tenths % 10}" else "${tenths / 10}"
}

private const val KILOBYTE = 1024L
private const val MEGABYTE = 1024L * 1024L
