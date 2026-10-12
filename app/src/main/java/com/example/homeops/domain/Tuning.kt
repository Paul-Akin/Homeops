package com.example.homeops.domain

import com.example.homeops.domain.model.TaskType

/**
 * Every tunable constant in the app, with the DESIGN.md section it comes from.
 *
 * Values are engineering judgment unless the comment says otherwise; tune them here,
 * never inline. (A Kotlin `object` is a singleton: think of it as a module of constants.)
 */
object Tuning {
    // ---- Section 5.1: prior time estimate t0 = a_k + b_k * A * s ----

    /** One row of the prior table: setup minutes, minutes per ft², default area in ft². */
    data class PriorRow(
        val setupMin: Double,
        val minPerFt2: Double,
        val defaultAreaFt2: Double,
    )

    /**
     * DESIGN.md Section 5.1 starting table. OTHER is not in the design table;
     * a flat 5 min is engineering judgment so manual "other" tasks get a sane prior.
     */
    val priorTable: Map<TaskType, PriorRow> =
        mapOf(
            TaskType.VACUUM to PriorRow(3.0, 0.04, 150.0),
            TaskType.MOP to PriorRow(5.0, 0.06, 100.0),
            TaskType.WIPE to PriorRow(1.0, 0.25, 15.0),
            TaskType.DUST to PriorRow(2.0, 0.05, 120.0),
            TaskType.SCRUB to PriorRow(3.0, 0.6, 12.0),
            TaskType.DECLUTTER to PriorRow(2.0, 0.3, 20.0),
            TaskType.DISHES to PriorRow(5.0, 0.0, 0.0),
            TaskType.LAUNDRY to PriorRow(10.0, 0.0, 0.0),
            TaskType.TRASH to PriorRow(3.0, 0.0, 0.0),
            TaskType.OTHER to PriorRow(5.0, 0.0, 0.0),
        )

    /** Severity factor s = SEVERITY_BASE + SEVERITY_STEP * (severity - 1), giving s in [0.6, 1.4]. */
    const val SEVERITY_BASE = 0.6
    const val SEVERITY_STEP = 0.2
    const val SEVERITY_MIN = 1
    const val SEVERITY_MAX = 5
    const val SEVERITY_DEFAULT = 3

    /** Section 4.2 / 5.1: default log-normal shape parameter. */
    const val SIGMA_LN_DEFAULT = 0.4

    // ---- Section 5.2: due-ness D(t) = 1 - exp(-t / tau) ----

    /** D* : a task is due when D >= this. */
    const val DUE_THRESHOLD = 0.8

    /** Section 5.5: a completion is "on time" when D <= this at completion. */
    const val ON_TIME_THRESHOLD = 0.9

    /** Section 6.4: nominal frequency clamp in days. */
    const val FREQUENCY_DAYS_MIN = 1.0
    const val FREQUENCY_DAYS_MAX = 365.0

    /** Default frequency offered when a user creates a task manually (engineering judgment). */
    const val FREQUENCY_DAYS_DEFAULT = 7.0

    // ---- Section 5.2: priority and tau adaptation (DecayScheduler) ----

    /** tau is always clamped to this range in days (Section 5.2 photo-adaptation clamp, applied to all updates). */
    const val TAU_DAYS_MIN = 0.5
    const val TAU_DAYS_MAX = 180.0

    /** Snoozed this many times in a row: user thinks the task is too frequent. */
    const val SNOOZE_STREAK = 3
    const val TAU_FACTOR_SNOOZED = 1.15

    /** Completed while D < EAGER_DUENESS this many times in a row: user wants it more often. */
    const val EAGER_STREAK = 3
    const val EAGER_DUENESS = 0.5
    const val TAU_FACTOR_EAGER = 0.9

    /** Task priority multiplier w_task range (engineering judgment, same as rooms). */
    const val TASK_WEIGHT_MIN = 0.1
    const val TASK_WEIGHT_MAX = 5.0

    // ---- Section 5.3: reminder timing (NotificationTimeLearner) ----

    /** Completions older than this are ignored when learning the preferred time. */
    const val LEARN_LOOKBACK_DAYS = 60L

    /** Learned time is used only with at least this many samples ... */
    const val LEARN_MIN_SAMPLES = 5

    /** ... and a mean resultant length R at least this large (R = 1: all at one time; R = 0: uniform). */
    const val LEARN_MIN_R = 0.5

    /** Notify this many minutes before the learned mean time. */
    const val LEAD_MINUTES = 30L

    /** Defaults for the user-editable reminder settings (minutes after midnight). */
    const val DEFAULT_REMINDER_MINUTE = 18 * 60
    const val DEFAULT_QUIET_START_MINUTE = 22 * 60
    const val DEFAULT_QUIET_END_MINUTE = 8 * 60

    /** Section 8: "Snooze 3 h" notification action. */
    const val SNOOZE_HOURS = 3

    /** Overdue nudge: D above the on-time limit, sent at most once a day this long after the digest. */
    const val OVERDUE_NUDGE_DELAY_HOURS = 3L

    // ---- Form validation (engineering judgment; Section 6.4 area clamp reused) ----
    const val NAME_MAX_LENGTH = 80
    const val AREA_FT2_MIN = 0.5
    const val AREA_FT2_MAX = 2000.0
    const val ROOM_WEIGHT_MIN = 0.1
    const val ROOM_WEIGHT_MAX = 5.0
    const val FLOOR_MIN = -3
    const val FLOOR_MAX = 100
}
