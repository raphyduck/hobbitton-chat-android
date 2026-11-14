package com.garfiec.librechat.shared.engine

import com.garfiec.librechat.core.data.engine.EngineChatSummary
import com.garfiec.librechat.core.data.engine.SearchMatch
import kotlin.test.Test
import kotlin.test.assertEquals

/** A search's results (14/11/2026): the title's matches, then the messages', and their excerpts. */
class SearchGroupingTest {

    private fun chat(id: String, at: Long, match: SearchMatch?) =
        EngineChatSummary(sessionId = id, title = id, lastActivityMillis = at, running = false, match = match)

    @Test
    fun theTitleFirstThenTheMessagesTheLatestFirstInEach() {
        val groups = groupSearch(
            listOf(
                chat("body_old", 100, SearchMatch(inTitle = false, inMessages = true)),
                chat("title_old", 200, SearchMatch(inTitle = true)),
                chat("both", 300, SearchMatch(inTitle = true, inMessages = true)),
                chat("body_new", 400, SearchMatch(inTitle = false, inMessages = true)),
                // Filtered on its title before the answer: under the title.
                chat("drawn", 50, null),
            ),
        )

        assertEquals(listOf(RecentDay.IN_TITLE, RecentDay.IN_MESSAGES), groups.map { it.day })
        assertEquals(listOf("both", "title_old", "drawn"), groups[0].chats.map { it.sessionId })
        assertEquals(listOf("body_new", "body_old"), groups[1].chats.map { it.sessionId })
    }

    @Test
    fun noMessageMatchNoMessageHeader() {
        val groups = groupSearch(listOf(chat("a", 1, SearchMatch(inTitle = true))))
        assertEquals(listOf(RecentDay.IN_TITLE), groups.map { it.day })
    }

    @Test
    fun theExcerptIsCutAtItsMarks() {
        assertEquals(
            listOf("Le " to false, "devis" to true, " Kandbaz, " to false, "cuisine" to true),
            excerptSpans("Le ⟦devis⟧ Kandbaz, ⟦cuisine⟧"),
        )
        assertEquals(listOf("sans marque" to false), excerptSpans("sans marque"))
        assertEquals(listOf("ouvert " to false, "jusqu'au bout" to true), excerptSpans("ouvert ⟦jusqu'au bout"))
        assertEquals(emptyList(), excerptSpans(""))
    }
}
