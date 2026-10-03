package com.garfiec.librechat.shared.engine

import com.garfiec.librechat.core.data.engine.EngineChatSummary

/** The drawer's headers over the recent conversations, in the order they are shown. */
internal enum class RecentDay { TODAY, YESTERDAY, PREVIOUS_WEEK, OLDER }

/** One header and the conversations under it, in the order the list gave them. */
internal data class RecentGroup(val day: RecentDay, val chats: List<EngineChatSummary>)

/**
 * Which header a conversation goes under, by its last activity on the phone's own calendar: days
 * are counted between local midnights, not in 24-hour slices, so 23:50 yesterday is « Yesterday »
 * at 00:10 today.
 *
 * [utcOffsetMillis] gives the local time zone's offset at an instant — a function rather than a
 * number, so a conversation from before a daylight-saving change is placed on its own day. A
 * conversation with no known activity goes under « Older »; one stamped in the future (a clock
 * ahead of the phone's) under « Today ».
 */
internal fun recentDay(
    lastActivityMillis: Long?,
    nowMillis: Long,
    utcOffsetMillis: (epochMillis: Long) -> Long,
): RecentDay {
    if (lastActivityMillis == null) return RecentDay.OLDER
    val days = localDay(nowMillis, utcOffsetMillis) - localDay(lastActivityMillis, utcOffsetMillis)
    return when {
        days <= 0L -> RecentDay.TODAY
        days == 1L -> RecentDay.YESTERDAY
        days <= PREVIOUS_WEEK_DAYS -> RecentDay.PREVIOUS_WEEK
        else -> RecentDay.OLDER
    }
}

/** The conversations under their headers; empty headers are left out. */
internal fun groupRecent(
    chats: List<EngineChatSummary>,
    nowMillis: Long,
    utcOffsetMillis: (epochMillis: Long) -> Long,
): List<RecentGroup> {
    val byDay = chats.groupBy { recentDay(it.lastActivityMillis, nowMillis, utcOffsetMillis) }
    return RecentDay.entries.mapNotNull { day -> byDay[day]?.let { RecentGroup(day, it) } }
}

/** The phone's time zone offset from UTC at [epochMillis], daylight saving included. */
internal expect fun localUtcOffsetMillis(epochMillis: Long): Long

private fun localDay(epochMillis: Long, utcOffsetMillis: (Long) -> Long): Long =
    (epochMillis + utcOffsetMillis(epochMillis)).floorDiv(MILLIS_PER_DAY)

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
private const val PREVIOUS_WEEK_DAYS = 7L
