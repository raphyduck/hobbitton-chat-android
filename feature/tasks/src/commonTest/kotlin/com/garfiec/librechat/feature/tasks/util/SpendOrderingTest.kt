package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.scheduler.ModelConsumption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** L'ordre du panneau de dépense, et le prix qu'il affiche à côté de chaque total. */
class SpendOrderingTest {

    private fun model(
        name: String,
        tokens: Long = 0,
        spend: Double? = 0.0,
        partial: Boolean = false,
    ) = ModelConsumption(
        model = name,
        tokens = tokens,
        spend = spend,
        isPriced = spend != null,
        isPartial = partial,
    )

    @Test
    fun `l'argent ordonne, pas le volume`() {
        // Le cas réel du 31/08/2026 : kimi-k3 a brûlé deux fois les jetons de claude-sonnet-5 sur
        // la semaine et coûté un dollar de moins. Le panneau se lit pour savoir où part l'argent.
        val ordre = listOf(
            model("kimi-k3", tokens = 13_278_628, spend = 7.7213),
            model("claude-sonnet-5", tokens = 6_647_688, spend = 8.9404),
            model("gpt-5.6-terra", tokens = 1_280_004, spend = 1.2686),
        ).byCostDescending().map { it.model }

        assertEquals(listOf("claude-sonnet-5", "kimi-k3", "gpt-5.6-terra"), ordre)
    }

    @Test
    fun `un modele sans prix tombe en bas, pas en haut ni a zero`() {
        // Son coût est inconnu, pas nul : le classer comme un zéro le mettrait au milieu des
        // modèles vraiment quasi gratuits, et le classer en tête serait pire encore.
        val ordre = listOf(
            model("sans-prix", tokens = 130_718, spend = null),
            model("minuscule", tokens = 1_200, spend = 0.0210),
        ).byCostDescending().map { it.model }

        assertEquals(listOf("minuscule", "sans-prix"), ordre)
    }

    @Test
    fun `entre deux modeles sans prix, le volume tranche`() {
        val ordre = listOf(
            model("petit", tokens = 302, spend = null),
            model("gros", tokens = 130_718, spend = null),
        ).byCostDescending().map { it.model }

        assertEquals(listOf("gros", "petit"), ordre)
    }

    @Test
    fun `le prix observe est la depense divisee par les jetons`() {
        // 7,7213 $ pour 13 278 628 jetons ≈ 0,5815 $ le million.
        val rate = model("kimi-k3", tokens = 13_278_628, spend = 7.7213).observedPricePerMillion()

        assertEquals(0.5815, rate!!, absoluteTolerance = 0.0001)
    }

    @Test
    fun `pas de prix affiche pour un modele non tarife`() {
        assertNull(model("sans-prix", tokens = 130_718, spend = null).observedPricePerMillion())
    }

    @Test
    fun `pas de prix affiche quand la depense n'est que partielle`() {
        // La moitié d'un prix rapportée à la totalité des jetons donne un taux trop bas — et un
        // taux qui paraît bas sans le dire est pire que pas de taux du tout.
        assertNull(model("partiel", tokens = 1_000_000, spend = 1.0, partial = true).observedPricePerMillion())
    }

    @Test
    fun `pas de prix affiche sans jetons a diviser`() {
        assertNull(model("vide", tokens = 0, spend = 0.5).observedPricePerMillion())
    }
}
