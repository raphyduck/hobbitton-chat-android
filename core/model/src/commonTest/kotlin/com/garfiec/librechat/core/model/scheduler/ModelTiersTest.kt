package com.garfiec.librechat.core.model.scheduler

import com.garfiec.librechat.core.model.engine.EngineModelRef
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The ladder of tiers the scheduler offers a conversation (25/10/2026), and the step above a model. */
class ModelTiersTest {

    private val tiers = Json.decodeFromString(
        ModelTiers.serializer(),
        """{"paliers": {"haut": "hobbitton-chat/claude-opus-5-5", "moyen": "hobbitton-chat/deepseek-v4-pro"}, "triage": true}""",
    )

    @Test
    fun theLadderIsOrderedLowestFirstWhateverTheServerSent() {
        assertEquals(listOf("moyen", "haut"), tiers.ladder)
    }

    @Test
    fun theStepAboveAModelIsFoundOnItsBareIdWhateverTheProvider() {
        assertEquals("haut" to "hobbitton-chat/claude-opus-5-5", tiers.strongerThan("deepseek-v4-pro"))
        assertEquals("haut" to "hobbitton-chat/claude-opus-5-5", tiers.strongerThan("hobbitton-gateway/DeepSeek-V4-Pro"))
        assertNull(tiers.strongerThan("claude-opus-5-5"), "the top of the ladder has nothing above")
        assertNull(tiers.strongerThan("kimi-k3"), "a model on no tier has no step above")
        assertNull(ModelTiers.NONE.strongerThan("deepseek-v4-pro"))
    }

    @Test
    fun aChoiceReadsWithNullsWhenTheServerHasNoTrieur() {
        val choice = Json.decodeFromString(ModelTierChoice.serializer(), """{"palier": null, "modele": null, "paliers": {}}""")
        assertNull(choice.tier)
        assertNull(choice.model)
    }

    @Test
    fun aQualifiedModelBecomesAnEngineRef() {
        assertEquals(EngineModelRef("hobbitton-chat", "deepseek-v4-pro"), "hobbitton-chat/deepseek-v4-pro".asModelRef())
        assertNull("deepseek-v4-pro".asModelRef())
        assertNull("hobbitton-chat/".asModelRef())
    }

    @Test
    fun lowestIsTheCheapestTierOrNothing() {
        val tiers = ModelTiers(mapOf("haut" to "hobbitton-chat/claude-opus-5-5", "moyen" to "hobbitton-chat/deepseek-v4-pro"))
        assertEquals("hobbitton-chat/deepseek-v4-pro", tiers.lowest)
        assertNull(ModelTiers.NONE.lowest)
    }
}
