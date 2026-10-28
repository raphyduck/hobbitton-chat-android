package com.garfiec.librechat.core.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustedImagesTest {

    @Test
    fun an_https_image_and_a_data_image_draw_on_their_own() {
        val text = "![WC](https://www.jacobdelafon.fr/media/eolia.jpg) ![b](data:image/png;base64,AAAA)"
        assertEquals(text, insecureImagesAsLinks(text))
        assertTrue(imageLoadsOnItsOwn("HTTPS://cdn.tld/x.png?w=800"))
    }

    @Test
    fun a_cleartext_image_becomes_a_link_the_person_opens_on_purpose() {
        assertEquals(
            "Voici [le devis (image)](http://x.tld/p.png) et la suite.",
            insecureImagesAsLinks("Voici ![le devis](http://x.tld/p.png) et la suite."),
        )
        assertEquals("[image (image)](ftp://x.tld/a.png)", insecureImagesAsLinks("![](ftp://x.tld/a.png)"))
        assertFalse(imageLoadsOnItsOwn("javascript:alert(1)"))
    }
}
