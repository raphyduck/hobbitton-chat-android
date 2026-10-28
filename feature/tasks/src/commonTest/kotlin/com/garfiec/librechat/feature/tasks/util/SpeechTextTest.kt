package com.garfiec.librechat.feature.tasks.util

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechTextTest {
    @Test
    fun `markers that a voice would spell out are gone`() {
        val spoken = speechText("## Bilan\n\nLe **total** fait *douze* euros, soit `12`.\n\n- un\n- deux\n\n> cité")
        assertEquals("Bilan\n\nLe total fait douze euros, soit 12.\n\nun\ndeux\n\ncité", spoken)
    }

    @Test
    fun `an image is its description and a link its text`() {
        val spoken = speechText("Voici ![la robe rouge](https://x.test/r.jpg) sur [le site](https://x.test) : https://x.test/p")
        assertEquals("Voici la robe rouge sur le site :", spoken)
    }

    @Test
    fun `code blocks are dropped and a table is read cell by cell`() {
        val spoken = speechText("Avant\n\n```kotlin\nval x = 1\n```\n\n| Nom | Prix |\n|---|---:|\n| Pain | 2 € |\n")
        assertEquals("Avant\n\nNom, Prix\nPain, 2 €", spoken)
    }

    @Test
    fun `underscores inside words stay`() {
        assertEquals("snake_case et souligné", speechText("snake_case et _souligné_"))
    }
}
