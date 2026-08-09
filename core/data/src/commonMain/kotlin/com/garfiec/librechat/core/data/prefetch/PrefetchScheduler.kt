package com.garfiec.librechat.core.data.prefetch

/**
 * Asks the platform to run a prefetch pass periodically while the device is idle and charging.
 *
 * Keep platform scheduler types out of this signature — it is what lets `:core:data` stay free of
 * WorkManager on the common side, and what lets a platform whose scheduling lives in the app target
 * bind something that reports itself unavailable.
 */
interface PrefetchScheduler {

    /**
     * True when this platform actually schedules anything. The readout hides the scheduled-run row
     * when it does not, following [AttachmentWarmer.isSupported] — a row reading "Never" forever is
     * worse than an absent one, because it looks like a feature that is broken rather than one that
     * is not there.
     */
    val isSupported: Boolean

    /**
     * Registers (or updates) the periodic job. Idempotent — safe to call on every settings change,
     * which is required rather than merely allowed: the network constraint is fixed when the job is
     * registered, so a change to the metered override has no effect until this runs again.
     */
    fun ensureScheduled(allowMetered: Boolean)

    /** Removes the job. Called when prefetching is switched off and on logout. */
    fun cancel()
}
