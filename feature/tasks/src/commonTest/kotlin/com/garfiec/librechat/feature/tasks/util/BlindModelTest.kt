package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The composer warns when, and only when, a photo is about to leave for a model that does not see. */
class BlindModelTest {

    private val blind = EngineSelectableModel("p", "text-only", "Text only", seesImages = false)
    private val seeing = EngineSelectableModel("p", "sees", "Sees")
    private val photo = StagedAttachment("1", "image/jpeg", "photo.jpg", ByteArray(1))
    private val pdf = StagedAttachment("2", "application/pdf", "doc.pdf", ByteArray(1))

    @Test
    fun aPhotoForABlindModelWarns() {
        assertTrue(imageStagedForBlindModel(blind, listOf(pdf, photo)))
    }

    @Test
    fun aSeeingModelNeverWarns() {
        assertFalse(imageStagedForBlindModel(seeing, listOf(photo)))
    }

    @Test
    fun noPhotoNoWarning() {
        assertFalse(imageStagedForBlindModel(blind, listOf(pdf)))
        assertFalse(imageStagedForBlindModel(blind, emptyList()))
    }

    @Test
    fun noModelKnownNoWarning() {
        assertFalse(imageStagedForBlindModel(null, listOf(photo)))
    }
}
