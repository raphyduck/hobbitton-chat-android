package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.data.engine.RecentMission
import com.garfiec.librechat.core.data.engine.RecentMissionStatus
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Chats and missions in one recents list — and the chats never disturbed by the guests. */
class DrawerRecentsTest {

    private val now = Instant.parse("2026-09-23T12:00:00Z")
    private val reference = RelativeTimeReference(now, TimeZone.UTC)

    private fun chat(id: String, at: Instant?) = DrawerConversationDisplayData(
        conversationId = id,
        title = id,
        model = null,
        endpoint = null,
        updatedAt = at,
        isActive = false,
        isFavorite = false,
        isPinned = false,
        tags = emptyList(),
    )

    private fun mission(id: String, at: Instant?) =
        DrawerMissionDisplayData(sessionId = id, title = id, updatedAt = at, status = RecentMissionStatus.Settled)

    private fun keys(rows: List<Pair<String, List<DrawerRecentRow>>>) =
        rows.map { (group, items) -> group to items.map { it.key } }

    @Test
    fun withoutMissionsTheChatsComeOutUnchanged() {
        val grouped = listOf("Today" to listOf(chat("c1", now - 1.hours)))

        val merged = mergeRecents(grouped, emptyList(), reference)

        assertEquals(listOf("Today" to listOf("c1")), keys(merged))
    }

    @Test
    fun aMissionIsSlottedBetweenTheChatsByRecency() {
        val grouped = listOf(
            "Today" to listOf(chat("c1", now - 1.hours), chat("c2", now - 5.hours)),
        )

        val merged = mergeRecents(grouped, listOf(mission("m1", now - 3.hours)), reference)

        assertEquals(listOf("Today" to listOf("c1", "mission_m1", "c2")), keys(merged))
    }

    @Test
    fun aMissionOpensItsOwnSectionWhenNoChatSharesItsDay() {
        // The real case of 23/09/2026: the last chat is weeks old, the missions are from this morning.
        val grouped = listOf("Previous 30 Days" to listOf(chat("c1", now - 26.days)))

        val merged = mergeRecents(grouped, listOf(mission("m1", now - 2.hours)), reference)

        assertEquals(
            listOf("Today" to listOf("mission_m1"), "Previous 30 Days" to listOf("c1")),
            keys(merged),
        )
    }

    @Test
    fun theChatsKeepTheirOwnOrderEvenWhenItIsNotChronological() {
        // Whatever order the chats arrive in is the chat list's business; merging must not re-sort it.
        val grouped = listOf(
            "Today" to listOf(chat("c-late", now - 5.hours), chat("c-early", now - 1.hours)),
        )

        val merged = mergeRecents(grouped, emptyList<DrawerMissionDisplayData>(), reference)
        val withMission = mergeRecents(grouped, listOf(mission("m-old", now - 20.days)), reference)

        assertEquals(listOf("c-late", "c-early"), keys(merged).single().second)
        assertEquals(listOf("c-late", "c-early"), keys(withMission).first().second)
    }

    @Test
    fun anUndatedMissionGoesLast() {
        val grouped = listOf("Today" to listOf(chat("c1", now - 1.hours)))

        val merged = mergeRecents(grouped, listOf(mission("m1", null)), reference)

        assertEquals(listOf("Today" to listOf("c1"), "Unknown" to listOf("mission_m1")), keys(merged))
    }

    @Test
    fun theSearchQueryFiltersMissionsByTitle() {
        val missions = listOf(
            RecentMission("a", "Compare les devis Engie", 1L, RecentMissionStatus.Settled),
            RecentMission("b", "Vérifie les sauvegardes", 2L, RecentMissionStatus.Running),
        )

        assertEquals(listOf("a"), missions.toDrawerMissions("engie").map { it.sessionId })
        assertEquals(listOf("a", "b"), missions.toDrawerMissions("").map { it.sessionId })
    }
}
