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

    // ---- Form validation (engineering judgment; Section 6.4 area clamp reused) ----
    const val NAME_MAX_LENGTH = 80
    const val AREA_FT2_MIN = 0.5
    const val AREA_FT2_MAX = 2000.0
    const val ROOM_WEIGHT_MIN = 0.1
    const val ROOM_WEIGHT_MAX = 5.0
    const val FLOOR_MIN = -3
    const val FLOOR_MAX = 100
}
