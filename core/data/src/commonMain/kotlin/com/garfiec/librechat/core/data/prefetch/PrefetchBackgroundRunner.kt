package com.garfiec.librechat.core.data.prefetch

import com.garfiec.librechat.core.common.identity.SessionManager
import com.garfiec.librechat.core.common.lifecycle.DeferredWorkWindow
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.logging.Diag
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How a scheduled run ended, for the readout and for the platform job's own logging. */
enum class PrefetchRunOutcome {
    /** Prefetching is switched off; the job should not have been scheduled at all. */
    DISABLED,

    /** Nobody is signed in. */
    NO_SESSION,

    /** The gate never opened — metered connection, battery saver, or the user is mid-request. */
    CONSTRAINTS_UNMET,

    /** A pass ran to completion. */
    COMPLETED,

    /** A pass was still going when the budget ran out. It continues if the process outlives us. */
    BUDGET_EXPIRED,

    /** The breaker is tripped even after a retry: the server is not answering. */
    STOPPED,
}

/**
 * Runs one prefetch pass on behalf of a platform scheduler, within a budget.
 *
 * It does **not** drive the engine directly. [PrefetchController]'s single collector is the only
 * thing serializing passes — the engine has no locking of its own — so this waits on that collector
 * instead of starting a second pass beside it.
 *
 * The subtlety is that opening the window is *itself* a trigger. In a process the scheduler spawned,
 * nothing has marked the UI started, so [DeferredWorkWindow.beginBackgroundRun] is what opens the
 * gate, and the gate's rising edge starts a pass with no request from anyone. Asking for one as well
 * would run two full passes back to back. So this waits for a pass to appear first, and only asks
 * when none does — which is the already-running-and-idle process, where there is no edge to ride.
 */
class PrefetchBackgroundRunner(
    private val sessionManager: SessionManager,
    private val controller: PrefetchController,
    private val engine: PrefetchEngine,
    private val deferredWorkWindow: DeferredWorkWindow,
    private val settingsDataStore: SettingsDataStore,
) {

    suspend fun runOnce(budget: Duration): PrefetchRunOutcome {
        if (!settingsDataStore.prefetchEnabled.first()) return PrefetchRunOutcome.DISABLED

        deferredWorkWindow.beginBackgroundRun()
        try {
            val session = withTimeoutOrNull(SESSION_WAIT) {
                sessionManager.current.filterNotNull().first()
            } ?: return PrefetchRunOutcome.NO_SESSION

            if (!awaitPassStart()) {
                controller.requestScheduledRun()
                if (!awaitPassStart()) return PrefetchRunOutcome.CONSTRAINTS_UNMET
            }

            var outcome = awaitPassEnd(budget, session.accountId.value)
            if (outcome == PrefetchRunOutcome.STOPPED) {
                // One retry with the breaker cleared. Beyond that the server really is not answering,
                // and the next scheduled run is hours away — a better time to try than now.
                controller.requestScheduledRun()
                if (awaitPassStart()) outcome = awaitPassEnd(budget, session.accountId.value)
            }
            Diag.d("Prefetch", attrs = mapOf("outcome" to outcome.name)) { "scheduled run finished" }
            return outcome
        } finally {
            deferredWorkWindow.endBackgroundRun()
        }
    }

    /**
     * Waits briefly for a pass to be under way.
     *
     * A pass that started and finished inside this grace reads as "never started", so the caller asks
     * for another one. That is wasteful only in appearance: the second pass finds the conversation
     * list already refreshed and nothing stale, which is the cheapest thing this engine does.
     */
    private suspend fun awaitPassStart(): Boolean =
        withTimeoutOrNull(PASS_START_GRACE) { controller.passInProgress.first { it } } != null

    private suspend fun awaitPassEnd(budget: Duration, accountId: String): PrefetchRunOutcome {
        val finished = withTimeoutOrNull(budget) { controller.passInProgress.first { !it } } != null
        if (!finished) return PrefetchRunOutcome.BUDGET_EXPIRED
        return if (engine.runState.value.stateFor(accountId) == PrefetchRunState.Stopped) {
            PrefetchRunOutcome.STOPPED
        } else {
            PrefetchRunOutcome.COMPLETED
        }
    }

    private companion object {
        /** Long enough for a cold process to read the account registry and form a session. */
        val SESSION_WAIT = 15.seconds
        val PASS_START_GRACE = 5.seconds
    }
}
