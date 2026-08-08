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
 * [backgroundRunsSupported] is false on platforms that have no way to keep a suspended process
 * running, where the latch would let work start and then be frozen mid-request with nothing to
 * finish or cancel it. Those platforms fall back to the live foreground signal, which is exactly the
 * behaviour that predates this class.
 */
class DeferredWorkWindow(
    foregroundSignal: ForegroundSignal,
    private val backgroundRunsSupported: Boolean,
) {

    private val uiStarted = MutableStateFlow(false)
    private val backgroundRuns = MutableStateFlow(0)

    /**
     * The two sources are OR-ed, and which one stands for "the app is available" is fixed at
     * construction — so only that one is collected. Combining all three and ignoring the unused
     * input would subscribe to a signal whose every change re-evaluates a term it cannot affect.
     */
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
