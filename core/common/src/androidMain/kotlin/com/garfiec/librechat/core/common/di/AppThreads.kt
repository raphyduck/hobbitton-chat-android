package com.garfiec.librechat.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The app's own threads, apart from `Dispatchers.IO` (27/10/2026).
 *
 * Raphaël, 27/10/2026, after v2026.10.50: a conversation opened on its grey placeholders and stayed
 * there, the composer without its model nor its shell label, and the journal had not one line for
 * it, not even « nothing kept on the device », the first thing an opening writes once the device's
 * cache has been looked at. That look is one hop to the IO threads for a file that did not exist:
 * the hop never came back, and nothing else was written to the journal afterwards (its writer runs
 * on the same threads). The 26/10 journal shows the same shape in its « attempt hung » episodes:
 * three reads of 8 s each that all timed out, the models' list failing at the same second, the
 * conversation reading at once after a restart.
 *
 * `Dispatchers.IO` is shared by everything in the process: Ktor's OkHttp engine reads every
 * response body there with a blocking read (one thread held for as long as a stream stays open or
 * stalls), Coil fetches and decodes there, the push service follows its stream there. Its 64
 * threads taken, every `withContext(Dispatchers.IO)` waits, silently and without a bound. So the
 * app no longer queues behind them:
 *
 * - [io]: the app's own blocking work (the transcript cache, the preferences, the journal, the
 *   repositories' hops). Elastic: a thread per task in flight, none kept idle beyond
 *   [KEEP_ALIVE_SECONDS], so a task never waits for another to finish; what blocks shows up in
 *   [ioThreads] and in the thread report instead of freezing the screens.
 * - [net]: the HTTP engines' own threads (Ktor's body readers), elastic too, so a stream left
 *   hanging on a dead connection costs a thread and never the next request.
 *
 * Daemon threads, named `eva-io-N` and `eva-net-N`, so a thread dump says whose they are.
 */
object AppThreads {

    private const val KEEP_ALIVE_SECONDS = 30L

    private val ioPool = elasticPool("eva-io")
    private val netPool = elasticPool("eva-net")

    val io: CoroutineDispatcher = ioPool.asCoroutineDispatcher()
    val net: CoroutineDispatcher = netPool.asCoroutineDispatcher()

    /** Threads alive in [io], busy or about to retire. */
    val ioThreads: Int get() = ioPool.poolSize

    /** Threads alive in [net]. */
    val netThreads: Int get() = netPool.poolSize

    private fun elasticPool(name: String): ThreadPoolExecutor {
        val count = AtomicInteger()
        val factory = ThreadFactory { task ->
            Thread(task, "$name-${count.incrementAndGet()}").apply { isDaemon = true }
        }
        return ThreadPoolExecutor(0, Int.MAX_VALUE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, SynchronousQueue(), factory)
    }
}
