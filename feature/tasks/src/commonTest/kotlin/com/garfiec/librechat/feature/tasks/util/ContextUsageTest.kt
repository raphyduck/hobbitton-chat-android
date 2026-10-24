package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.scheduler.ModelPrice
import com.garfiec.librechat.core.model.scheduler.ModelPrices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The context ring: the engine's count against the gateway's window, for the model of the next message. */
class ContextUsageTest {

    private val opus = EngineSelectableModel("p", "Claude-Opus-5-5", "Claude Opus 5.5")
    private val kimi = EngineSelectableModel("p", "kimi-k3", "Kimi K3")
    private val prices = ModelPrices(
        models = listOf(
            ModelPrice(model = "claude-opus-5-5", input = 5.0, contextWindow = 1_000_000),
            ModelPrice(model = "kimi-k3"),
        ),
    )

    @Test
    fun theShareOfTheWindowIsRoundedDown() {
        val usage = contextUsage(290_800, opus, prices)!!
        assertEquals(1_000_000L, usage.window)
        assertEquals(29, usage.percent)
        assertEquals(0.2908f, usage.fraction!!, 0.0001f)
        assertFalse(usage.nearlyFull)
    }

    @Test
    fun pastTheWindowReadsFullAndWarns() {
        val usage = contextUsage(1_300_000, opus, prices)!!
        assertEquals(100, usage.percent)
        assertEquals(1f, usage.fraction)
        assertTrue(usage.nearlyFull)
        assertTrue(contextUsage(800_000, opus, prices)!!.nearlyFull)
    }

    @Test
    fun anUnknownWindowGivesTheCountAlone() {
        val usage = contextUsage(12_000, kimi, prices)!!
        assertNull(usage.window)
        assertNull(usage.percent)
        assertNull(usage.fraction)
        assertNull(contextUsage(12_000, opus, ModelPrices.NONE)!!.window)
    }

    @Test
    fun nothingBeforeTheFirstAnswerReportsItsTokens() {
        assertNull(contextUsage(null, opus, prices))
        assertNull(contextUsage(0, opus, prices))
    }

    @Test
    fun countsAreWrittenShortWithAFrenchComma() {
        assertEquals("950", compactTokens(950))
        assertEquals("12 k", compactTokens(12_000))
        assertEquals("290,8 k", compactTokens(290_800))
        assertEquals("1 M", compactTokens(1_000_000))
        assertEquals("1,2 M", compactTokens(1_250_000))
    }
}
