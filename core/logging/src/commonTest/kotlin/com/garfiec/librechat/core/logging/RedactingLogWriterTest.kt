package com.garfiec.librechat.core.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.garfiec.librechat.core.logging.redact.LogRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a release build's console gets to see (F1, 26/09/2026): warnings and up, scrubbed, no traces. */
class RedactingLogWriterTest {

    private class Recording : LogWriter() {
        val lines = mutableListOf<Triple<Severity, String, Throwable?>>()
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            lines += Triple(severity, message, throwable)
        }
    }

    private fun release(delegate: Recording) = RedactingLogWriter(
        delegate = delegate,
        redactor = LogRedactor(),
        minSeverity = Severity.Warn,
        redact = true,
        stackTraces = false,
    )

    private fun debug(delegate: Recording) = RedactingLogWriter(
        delegate = delegate,
        redactor = LogRedactor(),
        minSeverity = Severity.Verbose,
        redact = false,
        stackTraces = true,
    )

    @Test
    fun `below the floor nothing is loggable`() {
        val writer = release(Recording())

        assertFalse(writer.isLoggable("Auth", Severity.Debug))
        assertFalse(writer.isLoggable("Auth", Severity.Info))
        assertTrue(writer.isLoggable("Auth", Severity.Warn))
        assertTrue(writer.isLoggable("Auth", Severity.Error))
    }

    @Test
    fun `a release message goes through the redactor`() {
        val delegate = Recording()

        release(delegate).log(Severity.Warn, "refused Bearer eyJabc.def.ghi at https://chat.example.com/api/x", "Auth", null)

        val written = delegate.lines.single().second
        assertFalse(written.contains("eyJabc"), written)
        assertFalse(written.contains("chat.example.com"), written)
    }

    @Test
    fun `a release throwable is summarised without its stack trace`() {
        val delegate = Recording()
        val failure = IllegalStateException("unexpected JSON input: {\"title\":\"private\"}")

        release(delegate).log(Severity.Error, "decode failed", "HTTP", failure)

        val (_, message, throwable) = delegate.lines.single()
        assertNull(throwable)
        assertTrue(message.startsWith("decode failed — "), message)
        assertFalse(message.contains("private"), message)
    }

    @Test
    fun `a debug build keeps the raw message and the throwable`() {
        val delegate = Recording()
        val failure = IllegalStateException("boom")

        val writer = debug(delegate)
        assertTrue(writer.isLoggable("Auth", Severity.Verbose))
        writer.log(Severity.Debug, "user@example.com signed in", "Auth", failure)

        val (_, message, throwable) = delegate.lines.single()
        assertEquals("user@example.com signed in", message)
        assertEquals(failure, throwable)
    }
}
