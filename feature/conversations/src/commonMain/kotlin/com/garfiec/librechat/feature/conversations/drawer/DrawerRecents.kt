package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.common.extensions.toRelativeDateGroup
import com.garfiec.librechat.core.data.engine.RecentMission
import kotlin.time.Instant

/**
 * Merges the engine's missions into the date-grouped chats, as the drawer's single recents list.
 *
 * **The chats keep exactly the order and the section they already had.** Missions are slotted in
 * front of the first chat older than them; nothing is re-sorted. Re-sorting everything by timestamp
 * would have been shorter to write and would have silently re-ordered the chat list for every
 * account, engine or not — this list is the app's spine, and missions are the guests in it.
 *
 * A mission's section comes from the same [toRelativeDateGroup] the chats were bucketed with, so a
 * mission from this morning lands under the same « Today » as this morning's chats. [missions] must
 * be newest first, which is how the engine source hands them over.
 */
internal fun mergeRecents(
    grouped: List<Pair<String, List<DrawerConversationDisplayData>>>,
    missions: List<DrawerMissionDisplayData>,
    reference: RelativeTimeReference,
): List<Pair<String, List<DrawerRecentRow>>> {
    if (missions.isEmpty()) {
        return grouped.map { (group, chats) -> group to chats.map { DrawerRecentRow.Chat(it) } }
    }

    val labelled = ArrayList<Pair<String, DrawerRecentRow>>(grouped.sumOf { it.second.size } + missions.size)
    var next = 0
    fun takeMission() {
        val mission = missions[next++]
        val group = mission.updatedAt?.toRelativeDateGroup(reference) ?: UNKNOWN_GROUP
        labelled += group to DrawerRecentRow.Mission(mission)
    }

    grouped.forEach { (group, chats) ->
        chats.forEach { chat ->
            while (next < missions.size && isNewer(missions[next].updatedAt, chat.updatedAt)) takeMission()
            labelled += group to DrawerRecentRow.Chat(chat)
        }
    }
    while (next < missions.size) takeMission()

    // `groupBy` keeps first-appearance order — the same call the chats were bucketed with.
    return labelled.groupBy({ it.first }, { it.second }).toList()
}

/** Maps the engine's missions to drawer rows, keeping only those whose title matches [query]. */
internal fun List<RecentMission>.toDrawerMissions(query: String): List<DrawerMissionDisplayData> =
    filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        .map { mission ->
            DrawerMissionDisplayData(
                sessionId = mission.sessionId,
                title = mission.title,
                updatedAt = mission.lastActivityMillis?.let { Instant.fromEpochMilliseconds(it) },
                status = mission.status,
            )
        }

/** A dated row beats an undated one; two undated rows keep the chat first. */
private fun isNewer(mission: Instant?, chat: Instant?): Boolean =
    mission != null && (chat == null || mission > chat)

/** The label the chats' own bucketing gives a conversation with no date (`groupedByDateBucket`). */
private const val UNKNOWN_GROUP = "Unknown"
