package com.garfiec.librechat.shared

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.garfiec.librechat.shared.navigation.sharedAppModule
import org.koin.core.context.startKoin
import platform.Foundation.NSLog

/**
 * LogWriter that routes Kermit output through NSLog so it appears in
 * the unified OS log (visible via `log stream` / Console.app / Xcode).
 */
private class IosNSLogWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val level = severity.name.first()
        NSLog("[$level/$tag] $message")
        throwable?.let { NSLog("[$level/$tag] ${it.stackTraceToString()}") }
    }
}

/**
 * Entry point for iOS to initialize Koin DI.
 * Call from Swift: IosKoinHelperKt.startIosKoin()
 */
fun startIosKoin() {
    // Route Kermit logs through NSLog for OS log visibility
    Logger.setLogWriters(IosNSLogWriter())

    // Install crash reporting hook early so all Kotlin exceptions get proper stack traces
    installCrashReporting()
    Logger.setMinSeverity(Severity.Debug)
    Logger.withTag("Koin").d { "startIosKoin: initializing Koin DI" }

    val app = startKoin {
        modules(iosSharedModule, sharedAppModule)
    }
    IosKoinAccessor.koin = app.koin
    Logger.withTag("Koin").d { "startIosKoin: Koin initialized successfully" }
}
