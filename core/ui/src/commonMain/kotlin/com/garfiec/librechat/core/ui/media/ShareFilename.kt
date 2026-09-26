package com.garfiec.librechat.core.ui.media

/** Longest name written under the share cache directory — the same bound `ArtifactDownloadHelper` uses. */
private const val MAX_SHARE_FILENAME_LENGTH = 100

/** An extension longer than this is not one; the name is cut as a whole instead of keeping it. */
private const val MAX_KEPT_EXTENSION_LENGTH = 10

private const val FALLBACK_FILENAME = "file"

/** RFC 2045 token characters, restricted to what a `type/subtype` pair sent to an Intent needs. */
private val MIME_TYPE = Regex("^[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*/[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*$")

private const val MAX_MIME_LENGTH = 127

private const val OCTET_STREAM = "application/octet-stream"

/**
 * Makes a server-supplied filename safe to write under the share cache directory.
 *
 * Strips path separators (so the name cannot leave the directory), control characters (a
 * newline in a name breaks the share sheet's display and some receivers' parsers), collapses `..`
 * and bounds the length to [MAX_SHARE_FILENAME_LENGTH] while keeping a short extension, because a
 * receiving app picks its handler from it (review C12, 26/09/2026). A name that ends up empty
 * becomes `file`.
 */
fun sanitizeShareFilename(filename: String): String {
    val base = filename.substringAfterLast('/').substringAfterLast('\\')
    val cleaned = base
        .filterNot { it.code < 0x20 || it.code == 0x7F }
        .replace("..", "_")
        .trim()
    val safe = if (cleaned.isEmpty() || cleaned == ".") FALLBACK_FILENAME else cleaned
    if (safe.length <= MAX_SHARE_FILENAME_LENGTH) return safe
    val dot = safe.lastIndexOf('.')
    val extension = if (dot > 0 && safe.length - dot <= MAX_KEPT_EXTENSION_LENGTH) safe.substring(dot) else ""
    val stem = if (extension.isEmpty()) safe else safe.substring(0, dot)
    return stem.take(MAX_SHARE_FILENAME_LENGTH - extension.length) + extension
}

/**
 * The MIME type to put on a share Intent for a server-supplied [mime]: the bare `type/subtype`
 * when it is well-formed, `application/octet-stream` otherwise. Parameters are dropped. A
 * fanciful value would make the share sheet fail or mislead the receiving app about the content
 * (review C12, 26/09/2026).
 */
fun shareMimeTypeOrDefault(mime: String?): String {
    val bare = mime?.substringBefore(';')?.trim().orEmpty()
    if (bare.isEmpty() || bare.length > MAX_MIME_LENGTH || !MIME_TYPE.matches(bare)) return OCTET_STREAM
    return bare.lowercase()
}
