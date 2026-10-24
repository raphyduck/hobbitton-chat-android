package com.garfiec.librechat.core.model.scheduler

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The scheduler's `tarifs`, as it answers since 24/10/2026: a price per model, and whether it sees images. */
class ModelPricesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun whetherAModelSeesImagesArrivesTrueFalseOrAbsent() {
        val payload = """
            {"unite":"dollars par million de jetons","modeles":[
            {"nom":"claude-opus-5-5","entree":5.0,"sortie":25.0,"cache_lecture":0.5,"cache_ecriture":6.25,"tarife":true,"voit_images":true},
            {"nom":"deepseek-v4-pro","entree":0.3,"sortie":1.2,"cache_lecture":null,"cache_ecriture":null,"tarife":true,"voit_images":false},
            {"nom":"kimi-k3","entree":null,"sortie":null,"cache_lecture":null,"cache_ecriture":null,"tarife":false,"voit_images":null}],
            "modeles_non_tarifes":["kimi-k3"]}
        """.trimIndent().replace("\n", "")

        val prices = json.decodeFromString<ModelPrices>(payload)

        assertTrue(prices.byModel.getValue("claude-opus-5-5").seesImages == true)
        assertEquals(false, prices.byModel.getValue("deepseek-v4-pro").seesImages)
        assertNull(prices.byModel.getValue("kimi-k3").seesImages)
        assertEquals(listOf("kimi-k3"), prices.unpriced)
    }

    @Test
    fun anOlderSchedulerThatSaysNothingLeavesItUnknown() {
        val prices = json.decodeFromString<ModelPrices>("""{"modeles":[{"nom":"x","entree":1.0}]}""")
        assertNull(prices.byModel.getValue("x").seesImages)
    }

    @Test
    fun theContextWindowIsTheGatewaysOrUnknown() {
        val prices = json.decodeFromString<ModelPrices>(
            """{"modeles":[{"nom":"opus","entree":5.0,"contexte":1000000},{"nom":"kimi","contexte":null},{"nom":"old","entree":1.0}]}""",
        )
        assertEquals(1_000_000L, prices.byModel.getValue("opus").contextWindow)
        assertNull(prices.byModel.getValue("kimi").contextWindow)
        assertNull(prices.byModel.getValue("old").contextWindow)
    }
}
