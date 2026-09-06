package com.garfiec.librechat.core.data.pricing

import com.garfiec.librechat.core.model.scheduler.ModelPrice
import com.garfiec.librechat.core.model.scheduler.ModelPrices
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The price table is shared by two pickers on two tabs, and both must survive its absence.
 *
 * Everything asserted here is about what happens when the table is *not* there: no scheduler, a
 * scheduler that failed, a scheduler configured after the app started. A price is decoration, and
 * decoration that can break a model picker is worse than no decoration at all.
 */
class ModelPriceCacheTest {

    private val opus = ModelPrices(models = listOf(ModelPrice("claude-opus-5", input = 5.0, output = 25.0)))

    @Test
    fun `with no source it answers an empty table and never asks`() = runTest {
        // iOS today: nothing binds a scheduler, so the cache has nowhere to ask (D-034).
        assertEquals(ModelPrices.NONE, ModelPriceCache(source = null).prices())
    }

    @Test
    fun `it asks once and serves the answer from memory afterwards`() = runTest {
        var calls = 0
        val cache = ModelPriceCache(source = { calls++; opus })

        assertEquals(5.0, cache.prices().byModel["claude-opus-5"]?.input)
        assertEquals(5.0, cache.prices().byModel["claude-opus-5"]?.input)

        // Two pickers open in the same minute; the table changes when a provider changes its rates.
        assertEquals(1, calls)
    }

    @Test
    fun `it asks again once the answer has aged past the ttl`() = runTest {
        var calls = 0
        var clock = 0L
        val cache = ModelPriceCache(source = { calls++; opus }, ttlMillis = 100, now = { clock })

        cache.prices()
        clock = 101
        cache.prices()

        assertEquals(2, calls)
    }

    @Test
    fun `a failure keeps the last table rather than emptying the picker`() = runTest {
        var fail = false
        val cache = ModelPriceCache(source = { if (fail) error("scheduler down") else opus })

        cache.prices()
        fail = true

        // The prices the user was looking at a second ago are still the truest thing available;
        // dropping them would blank every row because one request timed out.
        assertEquals(5.0, cache.prices().byModel["claude-opus-5"]?.input)
    }

    @Test
    fun `a first failure is not cached as an empty table`() = runTest {
        var fail = true
        val cache = ModelPriceCache(source = { if (fail) error("scheduler down") else opus })

        assertTrue(cache.prices().models.isEmpty())
        fail = false

        // Nothing was stamped, so the next picker asks again instead of waiting out a TTL for an
        // answer that never arrived.
        assertEquals(5.0, cache.prices().byModel["claude-opus-5"]?.input)
    }

    @Test
    fun `an empty answer is re-asked, so configuring a scheduler takes effect at once`() = runTest {
        var configured = false
        var calls = 0
        val cache = ModelPriceCache(source = { calls++; if (configured) opus else ModelPrices.NONE })

        cache.prices()
        configured = true

        assertEquals(5.0, cache.prices().byModel["claude-opus-5"]?.input)
        assertEquals(2, calls)
    }
}
