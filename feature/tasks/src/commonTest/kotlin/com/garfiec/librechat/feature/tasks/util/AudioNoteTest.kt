package com.garfiec.librechat.feature.tasks.util

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioNoteTest {

    @Test
    fun `typed words come first and each note is a quoted block naming its file`() {
        val out = outgoingMessageText(
            "Écoute ça",
            listOf(AudioNote("1", "memo.m4a", "Rappelle le plombier demain.")),
        )

        assertEquals(
            "Écoute ça\n\n> 🎙️ memo.m4a\n> Rappelle le plombier demain.",
            out,
        )
    }

    @Test
    fun `a note alone is a whole message`() {
        val out = outgoingMessageText("", listOf(AudioNote("1", "memo.ogg", "Bonjour.")))

        assertEquals("> 🎙️ memo.ogg\n> Bonjour.", out)
    }

    @Test
    fun `a multi-line transcription stays one quoted block`() {
        val out = outgoingMessageText(
            "",
            listOf(AudioNote("1", "a.mp3", "Première ligne.\n\nDeuxième ligne.")),
        )

        // The blank line is dropped rather than quoted: an empty `>` line would split the block.
        assertEquals("> 🎙️ a.mp3\n> Première ligne.\n> Deuxième ligne.", out)
    }

    @Test
    fun `no notes means the input travels untouched`() {
        assertEquals("Juste du texte", outgoingMessageText("  Juste du texte  ", emptyList()))
    }
}
