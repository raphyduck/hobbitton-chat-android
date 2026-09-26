package com.garfiec.librechat.feature.chat.components.artifact

/**
 * Test support for the sandboxed templates: HTML and React artifacts are delivered as the
 * `srcdoc` of a sandboxed frame, entity-escaped, so a test that wants to read what the artifact
 * will execute reads it back through this.
 */
internal object SandboxHostSupport {

    private val SRCDOC = Regex("""srcdoc="([^"]*)"""")

    /** The document inside the host's sandboxed frame, unescaped; fails when the host has none. */
    fun sandboxedDocument(host: String): String {
        val escaped = SRCDOC.find(host)?.groupValues?.get(1)
            ?: error("no sandboxed srcdoc frame in the host document")
        return unescapeHtml(escaped)
    }

    fun unescapeHtml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
}
