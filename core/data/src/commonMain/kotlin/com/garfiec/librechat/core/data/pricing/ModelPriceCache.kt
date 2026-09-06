package com.garfiec.librechat.core.data.pricing

import com.garfiec.librechat.core.model.scheduler.ModelPrices
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Where the price table comes from. One implementation today — the scheduler, which reads it off
 * the gateway — and the seam exists so the cache can be tested without an HTTP client, and so the
 * chat can hold the cache on a platform where no scheduler is wired at all.
 */
fun interface ModelPriceSource {
    /** Answers [ModelPrices.NONE] when this deployment has no scheduler; throws when one failed. */
    suspend fun fetch(): ModelPrices
}

/**
 * The gateway's price table, fetched once and shared by every screen that shows a model.
 *
 * Two model pickers ask for it — the chat's and the tasks tab's — and they open one after the other
 * in the same minute. Fetching per picker would be two round trips for a table that changes when a
 * provider changes its rates, which is to say a few times a year.
 *
 * **A failure is silent and keeps the last answer.** A price is decoration on a list that works
 * without it: an error banner over a model picker because a price could not be read would be worse
 * than the missing price. What is never silent is a *wrong* price — an unknown one is rendered as
 * words by [com.garfiec.librechat.core.ui.components.modelPriceLabel], never as a zero.
 *
 * @param source null on a platform or an install with no scheduler — the cache then answers
 *   [ModelPrices.NONE] without ever touching the network, which is what iOS does today (D-034).
 */
class ModelPriceCache(
    private val source: ModelPriceSource?,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val mutex = Mutex()
    private var cached: ModelPrices = ModelPrices.NONE
    private var fetchedAt: Long = 0

    /**
     * The table, from cache when it is fresh and from the scheduler otherwise.
     *
     * The lock is held across the call on purpose: two pickers opened at once ask one question
     * between them, and the second reads what the first brought back.
     */
    suspend fun prices(): ModelPrices {
        val source = source ?: return ModelPrices.NONE
        mutex.withLock {
            if (cached.models.isNotEmpty() && now() - fetchedAt < ttlMillis) return cached
            val fresh = runCatching { source.fetch() }.getOrNull() ?: return cached
            // An empty answer is not stamped: it is what an unconfigured scheduler returns, and
            // stamping it would keep the prices missing for a full TTL after one is configured.
            // Asking again costs nothing in that state — the repository answers without a request.
            if (fresh.models.isNotEmpty()) {
                cached = fresh
                fetchedAt = now()
            }
            return fresh.takeIf { it.models.isNotEmpty() } ?: cached
        }
    }

    private companion object {
        /**
         * Six hours. Prices move when a provider changes its rates — a few times a year — so this
         * is about surviving a day of use, not about freshness.
         */
        const val DEFAULT_TTL_MILLIS = 6L * 60 * 60 * 1000
    }
}
