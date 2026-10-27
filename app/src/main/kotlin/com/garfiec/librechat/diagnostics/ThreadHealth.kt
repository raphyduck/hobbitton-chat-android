package com.garfiec.librechat.diagnostics

import com.garfiec.librechat.core.common.di.AppThreads
import com.garfiec.librechat.core.common.diagnostics.threadReport
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.logging.LogOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val PROBE_EVERY_MS = 15_000L
private const val PROBE_ANSWER_MS = 3_000L

/** Threads in one of the app's pools past which the journal hears of it, then again at each doubling. */
private const val CROWDED_THREADS = 32

/**
 * The threads' health while the app is in front (27/10/2026), next to the main thread's watchdog.
 *
 * The 27/10 opening stalled on a hop to the IO threads that never came back, and nothing said why.
 * Every [PROBE_EVERY_MS], one empty task goes to the shared `Dispatchers.IO` (Coil, Ktor's internals,
 * DataStore still use it): no answer within [PROBE_ANSWER_MS] puts the threads' report in the
 * journal, once per episode. The app's own pools ([AppThreads]) are elastic and never make a task
 * wait: what can go wrong there is growth, so a pool past [CROWDED_THREADS], then past each doubling,
 * is reported the same way. The report names each pool's threads and where they stand: the next
 * journal says what held them.
 */
internal fun startThreadHealthWatch(): Job =
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        var starved = false
        var ioReported = CROWDED_THREADS
        var netReported = CROWDED_THREADS
        while (isActive) {
            delay(PROBE_EVERY_MS)
            val answered = withTimeoutOrNull(PROBE_ANSWER_MS) { withContext(Dispatchers.IO) { true } } ?: false
            if (!answered && !starved) report("Dispatchers.IO did not answer in ${PROBE_ANSWER_MS} ms")
            starved = !answered
            val io = AppThreads.ioThreads
            if (io >= ioReported) {
                report("$io threads in the app's IO pool")
                ioReported *= 2
            }
            val net = AppThreads.netThreads
            if (net >= netReported) {
                report("$net threads in the HTTP engines' pool")
                netReported *= 2
            }
        }
    }

private fun report(what: String) = runCatching {
    Diag.w(tag = "Threads", origin = LogOrigin.CLIENT) { "$what; ${threadReport()}" }
}
