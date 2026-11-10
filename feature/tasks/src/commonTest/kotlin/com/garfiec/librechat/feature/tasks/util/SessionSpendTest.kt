package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineCacheTokens
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EnginePartSnapshot
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.model.engine.EngineTokens
import com.garfiec.librechat.core.model.scheduler.ModelPrice
import com.garfiec.librechat.core.model.scheduler.ModelPrices
import com.garfiec.librechat.core.model.scheduler.PlatformRoles
import com.garfiec.librechat.core.model.scheduler.RoleModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The session's spend by role and model (08/11/2026), and the signature under an answer. */
class SessionSpendTest {

    private val roles = PlatformRoles(
        roles = mapOf(
            PlatformRoles.THINKER to RoleModels("claude-opus-5-5", listOf("claude-opus-5-5", "gpt-6.1-sol")),
            PlatformRoles.SAVANT to RoleModels("claude-fable-5-1", listOf("claude-fable-5-1")),
            PlatformRoles.SCOUT to RoleModels("deepseek-flash", listOf("deepseek-flash")),
        ),
    )
    private val opus = EngineModelRef("hobbitton-chat", "claude-opus-5-5")
    private val flash = EngineModelRef("hobbitton-chat", "deepseek-flash")
    private val fable = EngineModelRef("hobbitton-chat", "claude-fable-5-1")

    private fun step(id: String, model: EngineModelRef?, tokens: EngineTokens? = null, cost: Double? = null, tools: Int = 0) =
        buildList<EngineStreamEvent> {
            add(EngineStreamEvent.MessageStarted(id, "assistant", model = model, tokens = tokens, cost = cost))
            repeat(tools) { n -> add(EngineStreamEvent.PartUpdated(id, "$id-t$n", EnginePartSnapshot(type = "tool", tool = "x"))) }
        }

    @Test
    fun theSignatureNamesTheRoleOfTheModel() {
        assertEquals(ModelSignature(PlatformRoles.THINKER, "claude-opus-5-5"), signatureOf(opus, roles))
        assertEquals(ModelSignature(null, "mystery"), signatureOf(EngineModelRef("p", "mystery"), roles))
        assertNull(signatureOf(null, roles))
        assertEquals(PlatformRoles.THINKER, roles.roleOf("GPT-6.1-SOL"))
    }

    @Test
    fun theSpendFoldsTheParentAndItsChildrenByRoleThenModel() {
        val parent = step("m1", opus, EngineTokens(input = 1_000, output = 100), cost = 0.006, tools = 2) +
            EngineStreamEvent.MessageStarted("u1", "user") +
            step("m2", opus, EngineTokens(input = 2_000, output = 200), cost = 0.012, tools = 1)
        val scout = step("c1", flash, EngineTokens(input = 50_000, output = 2_000), cost = 0.0174, tools = 40)
        val savant = step("s1", fable, EngineTokens(input = 20_000, output = 3_000), cost = 0.35)

        val spend = sessionSpend(parent, listOf(scout, savant), roles, ModelPrices.NONE)

        // The pilot's role first (10/11/2026, the éclaireur), then the penseur, then the savant.
        assertEquals(listOf("deepseek-flash", "claude-opus-5-5", "claude-fable-5-1"), spend.lines.map { it.modelId })
        val opusLine = spend.lines.first { it.modelId == "claude-opus-5-5" }
        assertEquals(PlatformRoles.THINKER, opusLine.role)
        assertEquals(2, opusLine.calls)
        assertEquals(3, opusLine.tools)
        assertEquals(3_000L, opusLine.tokens.input)
        assertEquals(0.018, opusLine.cost!!, 1e-9)
        assertFalse(opusLine.estimated)
        assertEquals(2, spend.children)
        assertEquals(4, spend.calls)
        assertEquals(43, spend.tools)
        assertEquals(0.3854, spend.total!!, 1e-9)
        assertFalse(spend.estimated)
    }

    @Test
    fun aMessageTheEngineDidNotPriceIsEstimatedFromTheTable() {
        val prices = ModelPrices(
            models = listOf(ModelPrice("claude-opus-5-5", input = 4.0, output = 20.0, cacheRead = 0.2, cacheWrite = 5.0)),
        )
        val tokens = EngineTokens(
            input = 1_000_000,
            output = 100_000,
            reasoning = 100_000,
            cache = EngineCacheTokens(read = 1_000_000, write = 0),
        )
        val spend = sessionSpend(step("m1", opus, tokens, cost = 0.0), emptyList(), roles, prices)

        val line = spend.lines.single()
        assertTrue(line.estimated)
        assertEquals(4.0 + 4.0 + 0.2, line.cost!!, 1e-9)
        assertTrue(spend.estimated)
        // Unknown to the table too: no figure at all, and no total.
        val unknown =
            sessionSpend(step("m2", EngineModelRef("p", "mystery"), tokens, cost = null), emptyList(), roles, prices)
        assertNull(unknown.lines.single().cost)
        assertNull(unknown.total)
    }

    @Test
    fun dollarsPrintInCentsWithAComma() {
        assertEquals("< 0,01 $", formatDollars(0.004))
        assertEquals("0,02 $", formatDollars(0.015))
        assertEquals("12,40 $", formatDollars(12.4))
    }

    @Test
    fun aMergedRunKeepsTheLastModelAndSumsTheCosts() {
        val turns = listOf(
            ChatTurn.Assistant("a", model = flash, cost = 0.01),
            ChatTurn.Assistant("b", model = opus, cost = 0.02),
            ChatTurn.Assistant("c", model = null, cost = null),
        ).mergedAssistantRuns()
        val run = turns.single() as ChatTurn.Assistant
        assertEquals(opus, run.model)
        assertEquals(0.03, run.cost!!, 1e-9)
    }
}
