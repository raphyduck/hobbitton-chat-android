package com.garfiec.librechat.shared

import co.touchlab.kermit.Logger
import kotlin.experimental.ExperimentalNativeApi
import platform.Foundation.NSException
import platform.Foundation.NSLog

private val log = Logger.withTag("CrashReporting")

/**
 * Installs a Kotlin/Native unhandled exception hook that:
 * 1. Logs the full Kotlin stack trace via Kermit + NSLog (visible in Xcode/Console.app)
 * 2. Wraps the Kotlin exception as an NSException with readable class name, message,
 *    and stack trace — so iOS crash logs show meaningful Kotlin context instead of
 *    just "Kotlin_ObjCExport_trapOnUndeclaredException" with a SIGABRT.
 *
 * Call once during app startup (before any coroutines or UI work).
 */
@OptIn(ExperimentalNativeApi::class)
fun installCrashReporting() {
    setUnhandledExceptionHook { throwable ->
        // Log the full Kotlin stack trace so it's captured in OS logs
        val stackTrace = throwable.stackTraceToString()
        val exceptionName = throwable::class.qualifiedName ?: throwable::class.simpleName ?: "Unknown"
        val message = throwable.message ?: "(no message)"

        log.e { "FATAL: Unhandled Kotlin exception: $exceptionName: $message" }
        log.e { stackTrace }

        // Also write directly to NSLog in case Kermit writers aren't flushed
        NSLog("FATAL: Unhandled Kotlin exception: %@: %@", exceptionName, message)
        NSLog("Stack trace:\n%@", stackTrace)

        // Raise as NSException so iOS crash reporters capture a meaningful report
        // instead of a bare SIGABRT with no context
        NSException(
            name = "KotlinException: $exceptionName",
            reason = message,
            userInfo = null,
        ).raise()
    }
    log.d { "iOS crash reporting hook installed" }
}
