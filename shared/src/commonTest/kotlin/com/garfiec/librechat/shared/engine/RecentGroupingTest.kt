package com.garfiec.librechat.shared.engine

import com.garfiec.librechat.core.data.engine.EngineChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The drawer's day headers: counted between local midnights, in the phone's time zone, with the
 * conversations kept in the order the list gave them.
 */
class RecentGroupingTest {

    // 2026-10-03T10:00:00Z, read in UTC+2: 12:00 local.
    private val now = 1_791_021_600_000L
    private val paris: (Long) -> Long = { TWO_HOURS }
    private val utc: (Long) -> Long = { 0L }

    @Test
    fun earlierTheSameLocalDayIsToday() {
        assertEquals(RecentDay.TODAY, recentDay(now - 11 * HOUR, now, paris))
    }

    @Test
    fun justBeforeLocalMidnightIsYesterday() {
        // 21:50 UTC the day before = 23:50 local, the day before.
        val lateYesterday = now - 12 * HOUR - 10 * MINUTE
        assertEquals(RecentDay.YESTERDAY, recentDay(lateYesterday, now, paris))
    }

    @Test
    fun theSameInstantFallsOnDifferentDaysInDifferentZones() {
        // 22:30 UTC the day before: yesterday in UTC, but 00:30 today in UTC+2.
        val instant = now - 11 * HOUR - 30 * MINUTE
        assertEquals(RecentDay.YESTERDAY, recentDay(instant, now, utc))
        assertEquals(RecentDay.TODAY, recentDay(instant, now, paris))
    }

    @Test
    fun twoToSevenDaysBackIsThePreviousWeek() {
        assertEquals(RecentDay.PREVIOUS_WEEK, recentDay(now - 2 * DAY, now, paris))
        assertEquals(RecentDay.PREVIOUS_WEEK, recentDay(now - 7 * DAY, now, paris))
    }

    @Test
    fun eightDaysBackIsOlder() {
        assertEquals(RecentDay.OLDER, recentDay(now - 8 * DAY, now, paris))
    }

    @Test
    fun noActivityIsOlderAndTheFutureIsToday() {
        assertEquals(RecentDay.OLDER, recentDay(null, now, paris))
        assertEquals(RecentDay.TODAY, recentDay(now + DAY, now, paris))
    }

    @Test
    fun groupsFollowTheHeadersOrderAndKeepTheListOrder() {
        val chats = listOf(
            chat("a", now - HOUR),
            chat("b", now - 3 * DAY),
            chat("c", now - 2 * HOUR),
            chat("d", null),
            chat("e", now - DAY),
        )

        val groups = groupRecent(chats, now, paris)

        assertEquals(
            listOf(RecentDay.TODAY, RecentDay.YESTERDAY, RecentDay.PREVIOUS_WEEK, RecentDay.OLDER),
            groups.map { it.day },
        )
        assertEquals(listOf("a", "c"), groups.first().chats.map { it.sessionId })
        assertEquals(listOf("d"), groups.last().chats.map { it.sessionId })
    }

    @Test
    fun emptyHeadersAreLeftOut() {
        val groups = groupRecent(listOf(chat("a", now - 10 * DAY)), now, paris)

        assertEquals(listOf(RecentDay.OLDER), groups.map { it.day })
    }

    private fun chat(id: String, lastActivity: Long?) = EngineChatSummary(
        sessionId = id,
        title = id,
        lastActivityMillis = lastActivity,
        running = false,
    )

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val TWO_HOURS = 2 * HOUR
    }
}
