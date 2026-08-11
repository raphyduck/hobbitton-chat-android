package com.garfiec.librechat.core.data.prefetch

import com.garfiec.librechat.core.common.identity.SessionManager
import com.garfiec.librechat.core.logging.Diag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Starts and stops the prefetcher.
 *
 * Passes must keep running in the [com.garfiec.librechat.core.common.identity.Session]'s own scope.
 * That is what makes account attribution structural — `Session.accountId` is captured when identity
 * resolves and never changes, so no suspend point can move a warmed conversation onto another
 * account — and what cancels in-flight work on a switch, since ending a session cancels its scope.
 */
class PrefetchController(
    private val sessionManager: SessionManager,
    private val gate: PrefetchGate,
    private val engine: PrefetchEngine,
    appScope: CoroutineScope,
) {

    /**
     * Why a pass was asked for, when something other than the gate opening asked.
     *
     * The two differ only in how much they clear first: a person tapping "Warm now" wants everything
     * retried, while a scheduled run hours later wants the breaker cleared but not the once-per-process
     * reference-data mark — re-fetching endpoints, models and agents on every scheduled pass would be
     * most of its traffic.
     */
    private enum class RunTrigger {
        /** The gate's own rising edge, which starts a pass with nobody having asked for one. */
        GATE_OPENED,
        MANUAL,
        SCHEDULED,
    }

    /**
     * Run requests from settings and from the scheduler.
     *
     * No replay, and a single slot that drops the older request rather than suspending. Two
     * consequences, both wanted: a request made while the gate is shut has no subscriber at all and
     * is discarded on the spot rather than firing at whatever unrelated moment the gate next opens,
     * and repeated taps during a pass coalesce into one queued run instead of stacking up.
     */
    private val runRequests = MutableSharedFlow<RunTrigger>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _passInProgress = MutableStateFlow(false)

    /**
     * Whether a pass is running right now, for callers that have to wait one out.
     *
     * Published here rather than on the engine because this class owns the only call into it, so
     * this is the one place that sees a pass begin and end regardless of what triggered it — the
     * gate opening, a tap, or the scheduler.
     */
    val passInProgress: StateFlow<Boolean> = _passInProgress.asStateFlow()

    private val _completedPasses = MutableStateFlow(0)

    /**
     * How many passes have finished. [passInProgress] conflates, so a pass that begins and ends
     * between two reads of it is invisible; this only ever moves forward, so a waiter can tell
     * "nothing ran" from "it ran while I was not looking".
     */
    val completedPasses: StateFlow<Int> = _completedPasses.asStateFlow()

    init {
        appScope.launch {
            sessionManager.current.collectLatest { session ->
                if (session == null) return@collectLatest
                session.scope.launch {
                    // collectLatest, not collect: it is the only thing that cancels an in-flight
                    // pass when the gate closes. There is no per-condition teardown anywhere else.
                    gate.isOpen().collectLatest { open ->
                        if (!open) return@collectLatest
                        Diag.d("Prefetch") { "gate opened" }

                        // The rising-edge pass and every manual one run through a single collector,
                        // subscribed before the first pass starts. Subscribing first is what makes a
                        // tap during a long or rate-limited pass land in the buffer instead of being
                        // dropped for want of a collector — which is exactly when someone reaches
                        // for the button. Running them in one coroutine keeps them serialized
                        // against an engine that has no locking of its own, and inside collectLatest
                        // so the gate closing cancels a manual pass as it cancels an automatic one.
                        merge(flowOf(RunTrigger.GATE_OPENED), runRequests).collect { trigger ->
                            when (trigger) {
                                RunTrigger.MANUAL -> {
                                    Diag.d("Prefetch") { "manual run requested" }
                                    engine.resetForManualRun(session.accountId)
                                }
                                RunTrigger.SCHEDULED -> {
                                    Diag.d("Prefetch") { "scheduled run requested" }
                                    engine.resetBreaker(session.accountId)
                                }
                                RunTrigger.GATE_OPENED -> Unit
                            }
                            _passInProgress.value = true
                            try {
                                engine.run(session.accountId)
                            } finally {
                                // Also on cancellation: the gate closing mid-pass is the ordinary way
                                // a pass ends, and a waiter left believing one is still running would
                                // sit there until its own budget expired.
                                _passInProgress.value = false
                                _completedPasses.value += 1
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Asks for a pass now, clearing the breaker first.
     *
     * Honoured only while the gate is open — a request made on mobile data, in battery saver, or
     * with prefetching switched off is dropped rather than overriding the conditions the user set.
     * Callers disable the control instead of relying on that, so the drop is a backstop.
     */
    fun requestRun() {
        runRequests.tryEmit(RunTrigger.MANUAL)
    }

    /**
     * Asks for a pass on behalf of the scheduler, clearing the breaker but not the reference-data
     * mark.
     *
     * Needed because passes start on a *rising edge* of the gate: a scheduled run arriving into a
     * process that is already running with the gate open has nothing to trigger it, and would
     * otherwise wait out its whole budget while nothing happened.
     */
    fun requestScheduledRun() {
        runRequests.tryEmit(RunTrigger.SCHEDULED)
    }
}
