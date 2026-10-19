package com.garfiec.librechat.feature.tasks.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Programmées page's schedules, as the French strings say them (19/10/2026). */
class HumanScheduleTest {

    private val fr = ScheduleWords(
        days = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"),
        shortDays = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim."),
        shortMonths = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."),
        hour = "%1\$s h",
        hourMinute = "%1\$s h %2\$s",
        daily = "Tous les jours à %1\$s",
        weekdays = "Du lundi au vendredi à %1\$s",
        weekly = "Chaque %1\$s à %2\$s",
        monthly = "Le %1\$s de chaque mois à %2\$s",
        hourlyBetween = "Toutes les heures de %1\$s à %2\$s",
        hourly = "Toutes les heures",
        date = "%1\$s %2\$s %3\$s à %4\$s",
        and = "et",
    )

    @Test
    fun `common crons read as a sentence`() {
        assertEquals("Tous les jours à 7 h", humanCron("0 7 * * *", fr))
        assertEquals("Tous les jours à 7 h 05", humanCron("5 7 * * *", fr))
        assertEquals("Du lundi au vendredi à 8 h 30", humanCron("30 8 * * 1-5", fr))
        assertEquals("Chaque lundi à 9 h", humanCron("0 9 * * 1", fr))
        assertEquals("Chaque lundi, mercredi et dimanche à 9 h", humanCron("0 9 * * 1,3,0", fr))
        assertEquals("Chaque dimanche à 9 h", humanCron("0 9 * * 7", fr))
        assertEquals("Le 1 de chaque mois à 6 h", humanCron("0 6 1 * *", fr))
        assertEquals("Toutes les heures de 8 h à 20 h", humanCron("0 8-20 * * *", fr))
        assertEquals("Toutes les heures", humanCron("0 * * * *", fr))
    }

    @Test
    fun `a time zone prefix is read past`() {
        assertEquals("Tous les jours à 7 h", humanCron("CRON_TZ=Europe/Paris 0 7 * * *", fr))
    }

    @Test
    fun `an uncommon shape is left to the caller`() {
        assertNull(humanCron("*/15 * * * *", fr))
        assertNull(humanCron("0 7 * 1 *", fr))
        assertNull(humanCron("0 7 1 * 1", fr))
        assertNull(humanCron("0 7 * * mon", fr))
        assertNull(humanCron("0 7 * *", fr))
        assertNull(humanCron("0 */2 * * *", fr))
    }

    @Test
    fun `an ISO date reads with its day of week`() {
        // 20/10/2026 is a Tuesday, 01/03/2027 a Monday, 29/02/2028 a Tuesday.
        assertEquals("mar. 20 oct. à 9 h", humanDateTime("2026-10-20T09:00:00+02:00", fr))
        assertEquals("lun. 1 mars à 18 h 45", humanDateTime("2027-03-01 18:45", fr))
        assertEquals("mar. 29 févr. à 0 h", humanDateTime("2028-02-29T00:00", fr))
    }

    @Test
    fun `anything else is shown as given`() {
        assertEquals("demain", humanDateTime("demain", fr))
        assertEquals("2026-13-01T09:00", humanDateTime("2026-13-01T09:00", fr))
    }
}
