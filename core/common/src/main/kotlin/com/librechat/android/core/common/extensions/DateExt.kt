package com.librechat.android.core.common.extensions

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun String.toInstantOrNull(): Instant? =
    try { Instant.parse(this) } catch (_: Exception) { null }

fun Instant.toRelativeDateGroup(): String {
    val today = LocalDate.now()
    val date = atZone(ZoneId.systemDefault()).toLocalDate()
    val daysBetween = ChronoUnit.DAYS.between(date, today)
    return when {
        daysBetween == 0L -> "Today"
        daysBetween == 1L -> "Yesterday"
        daysBetween <= 7L -> "Previous 7 Days"
        daysBetween <= 30L -> "Previous 30 Days"
        else -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }
}

fun Long.toRelativeDateGroup(): String =
    Instant.ofEpochMilli(this).toRelativeDateGroup()
