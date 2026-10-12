package com.example.homeops.domain.model

import com.example.homeops.domain.Tuning

/**
 * User-editable reminder settings (DESIGN.md Sections 5.3 and 7, Settings screen).
 * Times are minutes after local midnight (0..1439), which keeps the math integer and testable.
 */
data class ReminderSettings(
    val enabled: Boolean = false,
    val defaultMinute: Int = Tuning.DEFAULT_REMINDER_MINUTE,
    val quietStartMinute: Int = Tuning.DEFAULT_QUIET_START_MINUTE,
    val quietEndMinute: Int = Tuning.DEFAULT_QUIET_END_MINUTE,
    /** Learn separate preferred times for weekdays and weekends. */
    val splitWeekdayWeekend: Boolean = true,
)
