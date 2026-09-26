package com.garfiec.librechat.core.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A server-supplied name or type never dictates where a shared file lands or what it claims to be (review C12). */
class ShareFilenameTest {

    @Test
    fun `an ordinary name passes unchanged`() {
        assertEquals("report 2026.pdf", sanitizeShareFilename("report 2026.pdf"))
    }

    @Test
    fun `path separators are stripped down to the last segment`() {
        assertEquals("c.txt", sanitizeShareFilename("a/b/c.txt"))
        assertEquals("c.txt", sanitizeShareFilename("a\\b\\c.txt"))
    }

    @Test
    fun `parent references are neutralised`() {
        assertEquals("_", sanitizeShareFilename(".."))
        assertEquals("_secret", sanitizeShareFilename("..secret"))
        assertEquals("a_.b", sanitizeShareFilename("a...b"))
    }

    @Test
    fun `control characters are removed`() {
        assertEquals("ab.txt", sanitizeShareFilename("a\u0000b\n.txt"))
    }

    @Test
    fun `an empty or dot-only name falls back`() {
        assertEquals("file", sanitizeShareFilename(""))
        assertEquals("file", sanitizeShareFilename("   "))
        assertEquals("file", sanitizeShareFilename("."))
        assertEquals("file", sanitizeShareFilename("dir/"))
    }

    @Test
    fun `a long name is bounded and keeps its extension`() {
        val long = "x".repeat(300) + ".png"
        val result = sanitizeShareFilename(long)
        assertEquals(100, result.length)
        assertTrue(result.endsWith(".png"))
    }

    @Test
    fun `a long name with a long extension is simply cut`() {
        val result = sanitizeShareFilename("y".repeat(200) + "." + "z".repeat(40))
        assertEquals(100, result.length)
        assertFalse(result.endsWith("z".repeat(40)))
    }

    @Test
    fun `a well-formed mime type is kept in lower case without parameters`() {
        assertEquals("image/png", shareMimeTypeOrDefault("image/png"))
        assertEquals("image/svg+xml", shareMimeTypeOrDefault("Image/SVG+XML; charset=utf-8"))
        assertEquals("application/vnd.ms-excel", shareMimeTypeOrDefault("application/vnd.ms-excel"))
    }

    @Test
    fun `a malformed mime type becomes octet-stream`() {
        listOf(null, "", "png", "image/", "/png", "image png", "image/png/extra", "a/b\nc", "x".repeat(200) + "/y")
            .forEach { assertEquals("application/octet-stream", shareMimeTypeOrDefault(it), "for '$it'") }
    }
}
