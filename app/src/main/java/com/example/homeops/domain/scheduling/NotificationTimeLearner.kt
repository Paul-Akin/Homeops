package com.example.homeops.domain.scheduling

import com.example.homeops.domain.Tuning
import com.example.homeops.domain.model.ReminderSettings
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Learns when the user likes to clean (DESIGN.md Section 5.3).
 *
 * Time of day is an angle, theta = 2 pi h / 24, so it is averaged as a vector:
 *   theta_bar = atan2(sum sin theta_k, sum cos theta_k)
 *   R = sqrt((sum cos theta_k)^2 + (sum sin theta_k)^2) / N
 * R is the mean resultant length: 1 when every sample is at the same time, near 0 when spread
 * around the clock. An arithmetic mean would put 23:30 and 00:30 at noon; this gives midnight.
 */
object NotificationTimeLearner {
    private const val MINUTES_PER_DAY = 24 * 60

    data class CircularStats(
        /** Mean time of day in fractional hours, 0 <= meanHour < 24. */
        val meanHour: Double,
        /** Mean resultant length, 0..1. */
        val r: Double,
        val n: Int,
    )

    /** The reminder time for one day and whether it was learned or fell back to the default. */
    data class ReminderTime(
        val minuteOfDay: Int,
        val learned: Boolean,
        val stats: CircularStats?,
    )

    /** Circular mean and resultant length of times of day given in fractional hours. Null if empty. */
    fun circularStats(hours: List<Double>): CircularStats? {
        if (hours.isEmpty()) return null
        var sumCos = 0.0
        var sumSin = 0.0
        for (h in hours) {
            val theta = 2.0 * PI * h / 24.0
            sumCos += cos(theta)
            sumSin += sin(theta)
        }
        val mean = atan2(sumSin, sumCos) // radians in (-pi, pi]
        val meanHour = ((mean * 24.0 / (2.0 * PI)) % 24.0 + 24.0) % 24.0
        val r = sqrt(sumCos * sumCos + sumSin * sumSin) / hours.size
        return CircularStats(meanHour, r, hours.size)
    }

    fun isWeekend(day: DayOfWeek): Boolean = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY

    /**
     * Quiet hours [start, end) may wrap past midnight (default 22:00 to 08:00). A time inside them
     * moves to the end of the quiet window, the next allowed minute. start == end means no quiet hours.
     */
    fun applyQuietHours(
        minuteOfDay: Int,
        quietStart: Int,
        quietEnd: Int,
    ): Int {
        val m = Math.floorMod(minuteOfDay, MINUTES_PER_DAY)
        val inQuiet =
            when {
                quietStart == quietEnd -> false
                quietStart < quietEnd -> m in quietStart until quietEnd
                else -> m >= quietStart || m < quietEnd
            }
        return if (inQuiet) quietEnd else m
    }

    /**
     * Reminder minute for [date]. With weekday/weekend split on, only completions on the same kind
     * of day count. If N >= 5 and R >= 0.5, remind 30 min before the circular mean; otherwise use
     * the default time. Quiet hours apply either way.
     */
    fun reminderTimeFor(
        date: LocalDate,
        completions: List<LocalDateTime>,
        settings: ReminderSettings,
    ): ReminderTime {
        val samples =
            if (settings.splitWeekdayWeekend) {
                val weekend = isWeekend(date.dayOfWeek)
                completions.filter { isWeekend(it.dayOfWeek) == weekend }
            } else {
                completions
            }
        val stats = circularStats(samples.map { it.toLocalTime().fractionalHours() })
        val learned = stats != null && stats.n >= Tuning.LEARN_MIN_SAMPLES && stats.r >= Tuning.LEARN_MIN_R
        val raw =
            if (learned) {
                (stats!!.meanHour * 60.0).roundToInt() - Tuning.LEAD_MINUTES.toInt()
            } else {
                settings.defaultMinute
            }
        val minute = applyQuietHours(raw, settings.quietStartMinute, settings.quietEndMinute)
        return ReminderTime(minute, learned, stats)
    }

    /**
     * The next digest time strictly after [now]: today's slot if it is still ahead and no digest has
     * gone out today, otherwise tomorrow's. [completions] older than the 60-day lookback are ignored.
     */
    fun nextReminder(
        now: ZonedDateTime,
        completions: List<Instant>,
        settings: ReminderSettings,
        digestSentToday: Boolean,
    ): Pair<ZonedDateTime, ReminderTime> {
        val cutoff = now.toInstant().minusSeconds(Tuning.LEARN_LOOKBACK_DAYS * 86_400)
        val local = completions.filter { it.isAfter(cutoff) }.map { LocalDateTime.ofInstant(it, now.zone) }
        val today = now.toLocalDate()
        val todayTime = reminderTimeFor(today, local, settings)
        val todayAt = today.atStartOfDay(now.zone).plusMinutes(todayTime.minuteOfDay.toLong())
        if (!digestSentToday && todayAt.isAfter(now)) return todayAt to todayTime
        val tomorrow = today.plusDays(1)
        val tomorrowTime = reminderTimeFor(tomorrow, local, settings)
        return tomorrow.atStartOfDay(now.zone).plusMinutes(tomorrowTime.minuteOfDay.toLong()) to tomorrowTime
    }

    /**
     * The first instant at or after [at] outside quiet hours. Used for snoozed reminders:
     * snoozing at 9:30 PM for 3 h lands at 12:30 AM, which moves to 8:00 AM that morning; a
     * time late in the evening (after quiet start) moves to quiet end on the next day.
     */
    fun nextAllowed(
        at: ZonedDateTime,
        settings: ReminderSettings,
    ): ZonedDateTime {
        val m = at.hour * 60 + at.minute
        val shifted = applyQuietHours(m, settings.quietStartMinute, settings.quietEndMinute)
        if (shifted == m) return at
        val sameDay = at.toLocalDate().atStartOfDay(at.zone).plusMinutes(shifted.toLong())
        return if (sameDay.isAfter(at)) sameDay else sameDay.plusDays(1)
    }

    /** True if [at] falls inside quiet hours (used to drop overdue nudges rather than delay them). */
    fun isQuiet(
        at: ZonedDateTime,
        settings: ReminderSettings,
    ): Boolean {
        val m = at.hour * 60 + at.minute
        return applyQuietHours(m, settings.quietStartMinute, settings.quietEndMinute) != m
    }

    private fun LocalTime.fractionalHours(): Double = hour + minute / 60.0 + second / 3600.0
}
