package com.garfiec.librechat.feature.tasks.util

import kotlin.test.Test
import kotlin.test.assertEquals

class DepositedAttachmentTest {

    @Test
    fun aDepositedVideoIsNamedWithItsSizeAndPath() {
        assertEquals(
            "🎬 fuite entrée.mp4 · 48 Mo · /srv/filemcp/eva/ses_1/fuite entrée.mp4",
            depositedLine("fuite entrée.mp4", "video/mp4", 48L * 1024 * 1024, "/srv/filemcp/eva/ses_1/fuite entrée.mp4"),
        )
        assertEquals(
            "📎 piece-1.pdf · 1,5 Mo · /srv/filemcp/eva/envois/piece-1.pdf",
            depositedLine(null, "application/pdf", 1_572_864, "/srv/filemcp/eva/envois/piece-1.pdf"),
        )
    }

    @Test
    fun sizesReadAsAPersonDoes() {
        assertEquals("12 o", humanSize(12))
        assertEquals("850 ko", humanSize(850L * 1024))
        assertEquals("2,3 Mo", humanSize((2.3 * 1024 * 1024).toLong()))
        assertEquals("120 Mo", humanSize(120L * 1024 * 1024))
    }

    @Test
    fun theLinesFollowTheWords() {
        assertEquals("Regarde ça\n\n🎬 a.mp4 · 1 Mo · /p", withDepositedLines("Regarde ça ", listOf("🎬 a.mp4 · 1 Mo · /p")))
        assertEquals("🎬 a.mp4 · 1 Mo · /p", withDepositedLines("", listOf("🎬 a.mp4 · 1 Mo · /p")))
        assertEquals("Salut", withDepositedLines("Salut", emptyList()))
    }
}
