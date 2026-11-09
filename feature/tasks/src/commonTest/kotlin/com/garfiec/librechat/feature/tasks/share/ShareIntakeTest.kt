package com.garfiec.librechat.feature.tasks.share

import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A share from another app (09/11/2026) waits for the conversation the person picks, and the
 * conversation that matches the pick takes it once.
 */
class ShareIntakeTest {

    private val photo = StagedAttachment(id = "p1", mime = "image/jpeg", filename = "recu.jpg", bytes = byteArrayOf(1))

    @Test
    fun aimed_at_a_new_conversation_only_the_blank_chat_takes_it() {
        val intake = ShareIntake()
        intake.offer(PendingShare(listOf(photo), text = "Classe ce reçu"))
        // Nobody takes an unaimed share: the chooser is still open.
        assertNull(intake.takeFor(null))
        assertNull(intake.takeFor("ses_1"))

        intake.aim(ShareTarget.NewConversation)
        assertNull(intake.takeFor("ses_1"))
        val taken = intake.takeFor(null)
        assertEquals(listOf(photo), taken?.attachments)
        assertEquals("Classe ce reçu", taken?.text)
        // Taken once: a recomposition or a second screen gets nothing.
        assertNull(intake.takeFor(null))
        assertNull(intake.pending.value)
    }

    @Test
    fun aimed_at_a_conversation_that_conversation_takes_it() {
        val intake = ShareIntake()
        intake.offer(PendingShare(listOf(photo)))
        intake.aim(ShareTarget.Conversation("ses_1"))
        assertNull(intake.takeFor(null))
        assertNull(intake.takeFor("ses_2"))
        assertEquals(listOf(photo), intake.takeFor("ses_1")?.attachments)
    }

    @Test
    fun an_empty_share_is_ignored_and_a_dismissed_one_dropped() {
        val intake = ShareIntake()
        intake.offer(PendingShare(emptyList(), text = "  "))
        assertNull(intake.pending.value)

        intake.offer(PendingShare(emptyList(), text = "Un texte"))
        assertEquals("Un texte", intake.pending.value?.text)
        intake.dismiss()
        assertNull(intake.pending.value)
    }

    @Test
    fun a_new_share_replaces_one_nobody_took_and_starts_unaimed() {
        val intake = ShareIntake()
        intake.offer(PendingShare(listOf(photo)))
        intake.aim(ShareTarget.NewConversation)
        intake.offer(PendingShare(emptyList(), text = "Plus récent", target = ShareTarget.Conversation("ses_9")))
        assertEquals("Plus récent", intake.pending.value?.text)
        assertNull(intake.pending.value?.target)
    }
}
