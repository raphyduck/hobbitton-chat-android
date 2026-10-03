package com.garfiec.librechat.feature.tasks.util

import kotlin.test.Test
import kotlin.test.assertEquals

/** The composer's model pill names the model alone: the provider stays in the picker. */
class ModelLabelTest {

    @Test
    fun aSlashPrefixIsDropped() {
        assertEquals("model-x", shortModelLabel("vendor/model-x"))
        assertEquals("model-x", shortModelLabel("gateway/vendor/model-x"))
    }

    @Test
    fun aColonPrefixIsDropped() {
        assertEquals("Model X", shortModelLabel("Vendor: Model X"))
    }

    @Test
    fun aPlainLabelIsKept() {
        assertEquals("Model X 2.5", shortModelLabel("Model X 2.5"))
    }

    @Test
    fun nothingLeftKeepsTheLabel() {
        assertEquals("vendor/", shortModelLabel("vendor/"))
    }
}
