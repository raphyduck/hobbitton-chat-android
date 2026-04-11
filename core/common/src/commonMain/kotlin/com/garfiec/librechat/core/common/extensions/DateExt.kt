package com.garfiec.librechat.core.common.extensions

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

fun String.toInstantOrNull(): Instant? =
    try { Instant.parse(this) } catch (_: Exception) { null }

fun Instant.toRelativeDateGroup(): String {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val date = toLocalDateTime(tz).date
    val daysBetween = date.daysUntil(today)
    return when {
        daysBetween == 0 -> "Today"
        daysBetween == 1 -> "Yesterday"
        daysBetween <= 7 -> "Previous 7 Days"
        daysBetween <= 30 -> "Previous 30 Days"
        else -> date.formatMonthYear()
    }
}

fun Long.toRelativeDateGroup(): String =
    Instant.fromEpochMilliseconds(this).toRelativeDateGroup()

private fun LocalDate.formatMonthYear(): String =
    formatMonthYear(monthNumber, year)
