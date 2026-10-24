package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.scheduler.ModelPrice
import com.garfiec.librechat.core.model.scheduler.ModelPrices
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The composer warns when, and only when, a photo is about to leave for a model the gateway says does not see. */
class BlindModelTest {

    private val blind = EngineSelectableModel("p", "DeepSeek-V4-Pro", "DeepSeek V4 Pro")
    private val seeing = EngineSelectableModel("p", "sees", "Sees")
    private val unknown = EngineSelectableModel("p", "unknown", "Unknown")
    private val prices = ModelPrices(
        models = listOf(
            // Lower-cased on the way in: the catalogue and the gateway have differed in case before.
            ModelPrice(model = "deepseek-v4-pro", input = 0.3, seesImages = false),
            ModelPrice(model = "sees", input = 3.0, seesImages = true),
            ModelPrice(model = "unknown", input = 1.0),
        ),
    )
    private val photo = StagedAttachment("1", "image/jpeg", "photo.jpg", ByteArray(1))
    private val pdf = StagedAttachment("2", "application/pdf", "doc.pdf", ByteArray(1))

    @Test
    fun aPhotoForABlindModelWarns() {
        assertTrue(imageStagedForBlindModel(blind, prices, listOf(pdf, photo)))
    }

    @Test
    fun aSeeingModelNeverWarns() {
        assertFalse(imageStagedForBlindModel(seeing, prices, listOf(photo)))
    }

    @Test
    fun aModelTheTableSaysNothingAboutIsTakenAsSeeing() {
        assertFalse(imageStagedForBlindModel(unknown, prices, listOf(photo)))
        assertFalse(imageStagedForBlindModel(blind, ModelPrices.NONE, listOf(photo)))
    }

    @Test
    fun noPhotoNoWarning() {
        assertFalse(imageStagedForBlindModel(blind, prices, listOf(pdf)))
        assertFalse(imageStagedForBlindModel(blind, prices, emptyList()))
    }

    @Test
    fun noModelKnownNoWarning() {
        assertFalse(imageStagedForBlindModel(null, prices, listOf(photo)))
    }
}
