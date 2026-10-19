package com.garfiec.librechat.feature.tasks.util

/**
 * The words a schedule is said with, in the screen's language (string resources, resolved by the
 * caller). Kept apart so the reading below is plain Kotlin, tested without Compose.
 */
data class ScheduleWords(
    /** Monday first, seven names, as they sit in a sentence (« lundi », « Monday »). */
    val days: List<String>,
    /** Monday first, seven short names (« lun. », « Mon »). */
    val shortDays: List<String>,
    /** January first, twelve short names (« janv. », « Jan »). */
    val shortMonths: List<String>,
    /** « %1$s h » / « %1$s:00 »: an hour on the hour. */
    val hour: String,
    /** « %1$s h %2$s » / « %1$s:%2$s »: an hour and its minutes. */
    val hourMinute: String,
    /** « Tous les jours à %1$s ». */
    val daily: String,
    /** « Du lundi au vendredi à %1$s ». */
    val weekdays: String,
    /** « Chaque %1$s à %2$s »: days joined by [and]. */
    val weekly: String,
    /** « Le %1$s de chaque mois à %2$s ». */
    val monthly: String,
    /** « Toutes les heures de %1$s à %2$s ». */
    val hourlyBetween: String,
    /** « Toutes les heures ». */
    val hourly: String,
    /** « %1$s %2$s %3$s à %4$s »: short day, day of month, short month, time. */
    val date: String,
    /** « et » / « and ». */
    val and: String,
)

/**
 * A five-field cron as a person says it (19/10/2026: the Programmées page showed « 0 7 * * 1-5 »),
 * or null when the shape is not one of the common ones, in which case the caller shows the cron.
 * A `CRON_TZ=…` prefix is read past.
 */
fun humanCron(cron: String, words: ScheduleWords): String? {
    val fields = cron.trim().split(Regex("\\s+")).dropWhile { it.startsWith("CRON_TZ=") }
    if (fields.size != CRON_FIELDS) return null
    val minute = fields[0]
    val hour = fields[1]
    val dayOfMonth = fields[2]
    val month = fields[3]
    val dayOfWeek = fields[4]
    val m = minute.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    if (month != "*") return null
    val h = hour.toIntOrNull()?.takeIf { it in 0..23 }
    if (h == null) {
        if (dayOfMonth != "*" || dayOfWeek != "*") return null
        if (hour == "*") return words.hourly
        val range = Regex("""(\d{1,2})-(\d{1,2})""").matchEntire(hour) ?: return null
        val (from, to) = range.destructured
        return words.hourlyBetween.fill(time(from.toInt(), m, words), time(to.toInt(), m, words))
    }
    val at = time(h, m, words)
    return when {
        dayOfMonth == "*" && dayOfWeek == "*" -> words.daily.fill(at)
        dayOfMonth == "*" && dayOfWeek == "1-5" -> words.weekdays.fill(at)
        dayOfMonth == "*" -> {
            val days = dayOfWeek.split(',').map { it.toIntOrNull()?.takeIf { d -> d in 0..7 } ?: return null }
            words.weekly.fill(joined(days.map { words.days[(it + 6) % 7] }, words.and), at)
        }
        dayOfWeek == "*" -> dayOfMonth.toIntOrNull()?.takeIf { it in 1..31 }?.let { words.monthly.fill(it.toString(), at) }
        else -> null
    }
}

/**
 * An ISO date and time as a person reads it (« lun. 20 oct. à 9 h »), or the text as given when it
 * is not one. The time is the one written: the scheduler writes its missions' times in their own
 * zone, which is the person's.
 */
fun humanDateTime(iso: String, words: ScheduleWords): String {
    val match = ISO_DATE_TIME.find(iso.trim()) ?: return iso
    val parts = match.groupValues.drop(1).map { it.toInt() }
    val y = parts[0]
    val mo = parts[1]
    val d = parts[2]
    val h = parts[3]
    val mi = parts[4]
    if (mo !in 1..12 || d !in 1..31) return iso
    return words.date.fill(words.shortDays[dayOfWeek(y, mo, d)], d.toString(), words.shortMonths[mo - 1], time(h, mi, words))
}

private fun time(hour: Int, minute: Int, words: ScheduleWords): String =
    if (minute == 0) words.hour.fill(hour.toString()) else words.hourMinute.fill(hour.toString(), minute.toString().padStart(2, '0'))

private fun joined(items: List<String>, and: String): String = when (items.size) {
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " $and " + items.last()
}

/** Monday = 0 … Sunday = 6, Sakamoto's method. */
private fun dayOfWeek(year: Int, month: Int, day: Int): Int {
    val offsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val y = if (month < 3) year - 1 else year
    val sundayFirst = (y + y / 4 - y / 100 + y / 400 + offsets[month - 1] + day) % 7
    return (sundayFirst + 6) % 7
}

/** `%1$s`, `%2$s`… filled in order, the way the string resources write them. */
private fun String.fill(vararg values: String): String =
    values.foldIndexed(this) { index, text, value -> text.replace("%${index + 1}\$s", value) }

private const val CRON_FIELDS = 5
private val ISO_DATE_TIME = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})""")
