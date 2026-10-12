package com.example.homeops.domain.scheduling

import com.example.homeops.domain.model.ReminderSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.min

class NotificationTimeLearnerTest {
    private val monday: LocalDate = LocalDate.of(2026, 10, 12)
    private val saturday: LocalDate = LocalDate.of(2026, 10, 17)
    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val settings = ReminderSettings(enabled = true)

    /** Distance between two times of day on the 24 h circle. */
    private fun circularHourDistance(
        a: Double,
        b: Double,
    ): Double {
        val d = abs(a - b) % 24.0
        return min(d, 24.0 - d)
    }

    private fun weekdayAt(
        hour: Int,
        minute: Int,
        count: Int,
    ): List<LocalDateTime> = (0 until count).map { monday.minusWeeks(it.toLong()).atTime(hour, minute) }

    // DESIGN.md Section 10 fixture: 23:30 and 00:30 average to 00:00, not 12:00.
    @Test
    fun `circular mean wraps around midnight`() {
        val stats = NotificationTimeLearner.circularStats(listOf(23.5, 0.5))!!
        assertTrue(circularHourDistance(stats.meanHour, 0.0) < 1e-9, "mean was ${stats.meanHour}")
        // Each sample is 0.5 h = 7.5 degrees off the mean: R = cos(7.5 deg) = 0.99144.
        assertEquals(0.99144, stats.r, 1e-5)
        assertEquals(2, stats.n)
    }

    @Test
    fun `identical samples give R = 1 and opposite samples give R = 0`() {
        val same = NotificationTimeLearner.circularStats(List(5) { 18.0 })!!
        assertEquals(18.0, same.meanHour, 1e-9)
        assertEquals(1.0, same.r, 1e-12)
        assertEquals(0.0, NotificationTimeLearner.circularStats(listOf(6.0, 18.0))!!.r, 1e-12)
        assertNull(NotificationTimeLearner.circularStats(emptyList()))
    }

    @Test
    fun `learned time is 30 minutes before the mean`() {
        val t = NotificationTimeLearner.reminderTimeFor(monday, weekdayAt(19, 0, 5), settings)
        assertTrue(t.learned)
        assertEquals(18 * 60 + 30, t.minuteOfDay)
    }

    @Test
    fun `fewer than 5 samples falls back to the default 6 PM`() {
        val t = NotificationTimeLearner.reminderTimeFor(monday, weekdayAt(19, 0, 4), settings)
        assertFalse(t.learned)
        assertEquals(18 * 60, t.minuteOfDay)
    }

    @Test
    fun `scattered times (R below 0_5) fall back to the default`() {
        // 0, 6, 12, 18 h cancel exactly; the extra 3 h sample leaves R = 1/5 = 0.2.
        val times = listOf(0, 6, 12, 18, 3).map { monday.atTime(it, 0) }
        val t = NotificationTimeLearner.reminderTimeFor(monday, times, settings)
        assertEquals(0.2, t.stats!!.r, 1e-9)
        assertFalse(t.learned)
        assertEquals(18 * 60, t.minuteOfDay)
    }

    @Test
    fun `weekdays and weekends are learned separately when split is on`() {
        val weekdayHistory = weekdayAt(19, 0, 5)
        val split = NotificationTimeLearner.reminderTimeFor(saturday, weekdayHistory, settings)
        assertFalse(split.learned, "weekday history must not set the weekend time")
        val pooled =
            NotificationTimeLearner.reminderTimeFor(
                saturday,
                weekdayHistory,
                settings.copy(splitWeekdayWeekend = false),
            )
        assertTrue(pooled.learned)
        assertEquals(18 * 60 + 30, pooled.minuteOfDay)
    }

    @Test
    fun `quiet hours shift to the end of the window, including across midnight`() {
        val q = { m: Int -> NotificationTimeLearner.applyQuietHours(m, 22 * 60, 8 * 60) }
        assertEquals(8 * 60, q(23 * 60))
        assertEquals(8 * 60, q(2 * 60))
        assertEquals(8 * 60, q(22 * 60))
        assertEquals(8 * 60, q(8 * 60))
        assertEquals(21 * 60 + 59, q(21 * 60 + 59))
        // Non-wrapping window and "no quiet hours".
        assertEquals(14 * 60, NotificationTimeLearner.applyQuietHours(13 * 60 + 30, 13 * 60, 14 * 60))
        assertEquals(23 * 60, NotificationTimeLearner.applyQuietHours(23 * 60, 0, 0))
    }

    @Test
    fun `a learned time inside quiet hours moves to the morning`() {
        // Mean 00:15, minus 30 min lead = 23:45, inside 22:00 to 08:00, so 08:00.
        val t = NotificationTimeLearner.reminderTimeFor(monday, weekdayAt(0, 15, 5), settings)
        assertTrue(t.learned)
        assertEquals(8 * 60, t.minuteOfDay)
    }

    // M2 acceptance: with nothing learned yet, the next reminder is at the configured time.
    @Test
    fun `next reminder is today if still ahead, otherwise tomorrow at the configured time`() {
        val custom = settings.copy(defaultMinute = 19 * 60 + 15)
        val morning = ZonedDateTime.of(monday.atTime(10, 0), zone)
        val (todayAt, _) = NotificationTimeLearner.nextReminder(morning, emptyList(), custom, digestSentToday = false)
        assertEquals(ZonedDateTime.of(monday.atTime(19, 15), zone), todayAt)

        val evening = ZonedDateTime.of(monday.atTime(20, 0), zone)
        val (tomorrowAt, _) =
            NotificationTimeLearner.nextReminder(
                evening,
                emptyList(),
                custom,
                digestSentToday = false,
            )
        assertEquals(ZonedDateTime.of(monday.plusDays(1).atTime(19, 15), zone), tomorrowAt)

        val (afterDigest, _) =
            NotificationTimeLearner.nextReminder(
                morning,
                emptyList(),
                custom,
                digestSentToday = true,
            )
        assertEquals(ZonedDateTime.of(monday.plusDays(1).atTime(19, 15), zone), afterDigest)
    }

    @Test
    fun `completions older than 60 days are ignored`() {
        val now = ZonedDateTime.of(monday.atTime(9, 0), zone)
        val old = (61..65).map { now.minusDays(it.toLong()).withHour(7).toInstant() }
        val (_, t) = NotificationTimeLearner.nextReminder(now, old, settings.copy(splitWeekdayWeekend = false), false)
        assertFalse(t.learned)
        val recent =
            (1..5).map {
                now
                    .minusDays(it.toLong())
                    .withHour(12)
                    .withMinute(0)
                    .toInstant()
            }
        val (at, t2) =
            NotificationTimeLearner.nextReminder(
                now,
                recent,
                settings.copy(splitWeekdayWeekend = false),
                false,
            )
        assertTrue(t2.learned)
        assertEquals(ZonedDateTime.of(monday.atTime(11, 30), zone), at)
    }

    @Test
    fun `isQuiet detects quiet instants`() {
        assertTrue(NotificationTimeLearner.isQuiet(ZonedDateTime.of(monday.atTime(23, 0), zone), settings))
        assertFalse(NotificationTimeLearner.isQuiet(ZonedDateTime.of(monday.atTime(12, 0), zone), settings))
    }

    @Test
    fun `snoozed reminder skips quiet hours`() {
        val at = { d: LocalDate, h: Int, m: Int -> ZonedDateTime.of(d.atTime(h, m), zone) }
        // 21:30 + 3 h = 00:30 Tuesday, inside quiet hours: moves to 08:00 Tuesday.
        assertEquals(
            at(monday.plusDays(1), 8, 0),
            NotificationTimeLearner.nextAllowed(at(monday.plusDays(1), 0, 30), settings),
        )
        // 23:15 Monday: next allowed is 08:00 Tuesday, not 08:00 Monday (in the past).
        assertEquals(at(monday.plusDays(1), 8, 0), NotificationTimeLearner.nextAllowed(at(monday, 23, 15), settings))
        // Outside quiet hours: unchanged.
        assertEquals(at(monday, 15, 0), NotificationTimeLearner.nextAllowed(at(monday, 15, 0), settings))
    }
}
