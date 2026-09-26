package com.garfiec.librechat.core.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.garfiec.librechat.core.logging.redact.LogRedactor

/**
 * The console sink — Logcat, NSLog — with a floor and a scrub (finding F1, 26/09/2026).
 *
 * The persistent file sink has always gone through [LogRedactor]; the console never did, and Kermit's
 * platform writer takes every severity. So a release build printed URIs, file names, paths and
 * identifiers to a log readable by anyone with ADB or a system bug report. This wrapper puts the
 * console on the same footing: below [minSeverity] nothing is written at all, and what is written
 * passes through the same redactor as the file when [redact] is set. A throwable is summarised to
 * its type and redacted message rather than a stack trace when [stackTraces] is off, because a
 * serialization failure echoes the response body into its message.
 *
 * Two knobs rather than one build flag, because the two platforms tell debug from release
 * differently (`BuildConfig.DEBUG`, `Platform.isDebugBinary`) and a debug build wants the raw,
 * verbose console it always had. The global `Logger.setMinSeverity` is deliberately not used for
 * this: it would floor the file sink too, and the diagnostic export exists to carry the detail.
 */
class RedactingLogWriter(
    private val delegate: LogWriter,
    private val redactor: LogRedactor,
    private val minSeverity: Severity,
    private val redact: Boolean,
    private val stackTraces: Boolean,
) : LogWriter() {

    override fun isLoggable(tag: String, severity: Severity): Boolean =
        severity >= minSeverity && delegate.isLoggable(tag, severity)

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val text = if (redact) redactor.redact(message) else message
        if (stackTraces || throwable == null) {
            delegate.log(severity, text, tag, throwable)
        } else {
            val summary = throwable.toString().let { if (redact) redactor.redact(it) else it }
            delegate.log(severity, "$text — $summary", tag, null)
        }
    }
}
