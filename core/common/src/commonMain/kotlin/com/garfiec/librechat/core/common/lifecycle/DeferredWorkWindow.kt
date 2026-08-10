package com.garfiec.librechat.core.common.lifecycle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

/**
 * Whether deferred background work — today, cache prefetching — may run at all.
 *
 * This is deliberately *not* "is the app on screen". Two sources open the window:
 *
 * - **The UI having started**, which latches and never clears, so work continues after the app
 *   leaves the screen. It latches rather than tracking the foreground because the thing it needs to
 *   exclude is cold start, not backgrounding: nothing may run before the first composition, which is
 *   the worst possible moment to compete for the main thread.
 * - **An explicit background run**, counted rather than boolean so overlapping runs can't have one
 *   ending close the window on another.
 *
 * The second source is what stops the first from being a trap. A process spawned by a scheduled job
 * never composes, so the latch would stay false, the window would stay shut, and the job would wake
 * the process, do nothing, and exit reporting success — silently, because a shut window is
 * indistinguishable from a busy one at the far end.
 *
 * [BackgroundWorkSupport.UNSUPPORTED] platforms fall back to the live foreground signal. Keep this
 * an enum, not a `Boolean`: the Koin graph verifier cannot resolve a primitive constructor
 * parameter, and allowlisting `Boolean` would blind it to every genuinely missing one in this module.
 */
enum class BackgroundWorkSupport {
    /** The platform can run work in a process with no UI — Android, via WorkManager. */
    SUPPORTED,

    /**
     * The platform suspends a backgrounded process, so latching the window would start work and
     * then freeze it mid-request with nothing to finish or cancel it.
     */
    UNSUPPORTED,
}

class DeferredWorkWindow(
    foregroundSignal: ForegroundSignal,
    support: BackgroundWorkSupport,
) {

    private val uiStarted = MutableStateFlow(false)
    private val backgroundRuns = MutableStateFlow(0)

    // Which source stands for "the app is available" is fixed at construction, so only that one is
    // collected — subscribing to the other would re-evaluate the combine on changes it cannot affect.
    private val appAvailable: Flow<Boolean> = when (support) {
        BackgroundWorkSupport.SUPPORTED -> uiStarted
        BackgroundWorkSupport.UNSUPPORTED -> foregroundSignal.isForeground
    }

    val isOpen: Flow<Boolean> = combine(appAvailable, backgroundRuns) { available, runs ->
        available || runs > 0
    }.distinctUntilChanged()

    /** Called once the UI has composed. Latches — there is no un-starting. */
    fun markUiStarted() {
        uiStarted.value = true
    }

    fun beginBackgroundRun() {
        backgroundRuns.update { it + 1 }
    }

    /** Floored at zero so an unbalanced call can't leave the counter negative and the window stuck. */
    fun endBackgroundRun() {
        backgroundRuns.update { (it - 1).coerceAtLeast(0) }
    }
}
