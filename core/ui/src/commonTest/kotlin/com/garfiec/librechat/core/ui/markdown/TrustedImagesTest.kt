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

class SplitOutImagesTest {

    @Test
    fun an_image_at_the_end_of_a_list_item_leaves_the_text_before_and_after_as_markdown() {
        val text = "- **Verseau 2**, 92,63 € · [fiche](https://x.tld/v) ![Verseau](https://x.tld/v.jpg)\n" +
            "- **Grohe**, 131,42 € · [fiche](https://x.tld/g)\n![](https://x.tld/g.jpg)\n\nVoilà."
        assertEquals(
            listOf(
                ProsePart.Text("- **Verseau 2**, 92,63 € · [fiche](https://x.tld/v)"),
                ProsePart.Image("https://x.tld/v.jpg", "Verseau"),
                ProsePart.Text("- **Grohe**, 131,42 € · [fiche](https://x.tld/g)"),
                ProsePart.Image("https://x.tld/g.jpg", ""),
                ProsePart.Text("Voilà."),
            ),
            splitOutImages(text),
        )
    }

    @Test
    fun a_list_item_that_is_only_an_image_leaves_no_bare_bullet() {
        assertEquals(
            listOf(
                ProsePart.Text("Deux vues :"),
                ProsePart.Image("https://x.tld/1.png", "a"),
                ProsePart.Image("https://x.tld/2.png", "b"),
            ),
            splitOutImages("Deux vues :\n- ![a](https://x.tld/1.png)\n- ![b](https://x.tld/2.png)"),
        )
    }

    @Test
    fun prose_without_an_image_and_a_cleartext_image_stay_whole() {
        assertEquals(listOf(ProsePart.Text("Rien à voir.")), splitOutImages("Rien à voir."))
        val lien = insecureImagesAsLinks("Voici ![le devis](http://x.tld/p.png) et la suite.")
        assertEquals(listOf(ProsePart.Text(lien)), splitOutImages(lien))
    }

    @Test
    fun a_data_image_is_taken_out_like_an_https_one() {
        assertEquals(
            listOf(ProsePart.Text("QR :"), ProsePart.Image("data:image/png;base64,AAAA", "billet")),
            splitOutImages("QR : ![billet](data:image/png;base64,AAAA)"),
        )
    }
}
