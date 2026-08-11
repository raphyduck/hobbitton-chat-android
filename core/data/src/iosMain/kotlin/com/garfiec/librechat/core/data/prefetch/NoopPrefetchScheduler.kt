package com.garfiec.librechat.core.data.prefetch

/**
 * iOS has no scheduler here yet, and says so rather than pretending.
 *
 * `BGTaskScheduler` handlers must be registered in the app target before launch finishes, so the
 * scheduling half belongs in Swift rather than in this module — a Kotlin object cannot register it.
 * Until that lands, [isSupported] is false, which is what keeps the readout from showing a
 * scheduled-run row that could only ever say "Never".
 */
class NoopPrefetchScheduler : PrefetchScheduler {

    override val isSupported: Boolean = false

    override fun ensureScheduled(allowMetered: Boolean) = Unit

    override fun cancel() = Unit
}
