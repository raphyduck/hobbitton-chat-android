package com.garfiec.librechat.core.data.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The link of a server push notification (ntfy, 19/10/2026): it names the conversation to open. */
class ConversationLinkTest {

    @Test
    fun `a conversation link names its session`() {
        assertEquals(
            "ses_eac83ee47ffeb93z9Lim07dhou",
            sessionOfConversationLink("at.hobbitton.chat", "conversation", "/ses_eac83ee47ffeb93z9Lim07dhou"),
        )
    }

    @Test
    fun `anything else is no conversation`() {
        assertNull(sessionOfConversationLink("at.hobbitton.chat", "oauth", "/ses_eac83ee47ffe"))
        assertNull(sessionOfConversationLink("https", "conversation", "/ses_eac83ee47ffe"))
        assertNull(sessionOfConversationLink("at.hobbitton.chat", "conversation", "/"))
        assertNull(sessionOfConversationLink("at.hobbitton.chat", "conversation", "/../settings"))
        assertNull(sessionOfConversationLink("at.hobbitton.chat", "conversation", null))
    }
}
