package com.garfiec.librechat.feature.tasks.util

import androidx.compose.runtime.Composable
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_age_days
import com.garfiec.librechat.feature.tasks.resources.tasks_age_hours
import com.garfiec.librechat.feature.tasks.resources.tasks_age_minutes
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round
import kotlin.time.Clock

/** Four decimals, because a day of the cheap models lands well under a cent. */
internal fun money(value: Double): String {
    val cents = round(value * 10_000).toLong()
    return "${cents / 10_000}.${(cents % 10_000).toString().padStart(4, '0')} $"
}

/** « 7 748 977 » — a raw seven-digit number is unreadable at a glance. */
internal fun groupThousands(value: Long): String =
    value.toString().reversed().chunked(3).joinToString("\u202f").reversed()

/** A row's age, compact like a messaging list: minutes under an hour, hours under a day, then days. */
@Composable
internal fun missionAge(createdAtMillis: Long): String {
    val minutes = ((Clock.System.now().toEpochMilliseconds() - createdAtMillis) / MILLIS_PER_MINUTE)
        .coerceAtLeast(0)
    return when {
        minutes < MINUTES_PER_HOUR -> stringResource(Res.string.tasks_age_minutes, minutes)
        minutes < MINUTES_PER_DAY -> stringResource(Res.string.tasks_age_hours, minutes / MINUTES_PER_HOUR)
        else -> stringResource(Res.string.tasks_age_days, minutes / MINUTES_PER_DAY)
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24 * 60L
