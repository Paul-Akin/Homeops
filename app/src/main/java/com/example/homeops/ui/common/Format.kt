package com.example.homeops.ui.common

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Display-only formatting. No business rules here. */
object Format {
    private val dateTime: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    fun dateTime(
        instant: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = dateTime.format(instant.atZone(zone))

    /** "never", "just now", "45 min ago", "5 h ago", "3 d ago". */
    fun ago(
        instant: Instant?,
        now: Instant,
    ): String {
        if (instant == null) return "never"
        val minutes = Duration.between(instant, now).toMinutes().coerceAtLeast(0)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 48 * 60 -> "${minutes / 60} h ago"
            else -> "${minutes / (24 * 60)} d ago"
        }
    }

    fun minutes(min: Double): String = "${min.roundToInt().coerceAtLeast(1)} min"

    /** Frequency in days for display: "7", "3.5". */
    fun days(days: Double): String {
        val rounded = (days * 10).roundToInt() / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    fun ft2(area: Double?): String = area?.let { "${days(it)} ft²" } ?: ""
}
