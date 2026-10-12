package com.example.homeops.domain.scheduling

import com.example.homeops.domain.Tuning

/**
 * Priority ordering and behavioral tau adaptation (DESIGN.md Section 5.2).
 * Due-ness itself, D(t) = 1 - exp(-t / tau), lives in [Dueness].
 */
object DecayScheduler {
    /** Priority score P = D * w_room * w_task, used to order the Today list. */
    fun priority(
        dueness: Double,
        roomWeight: Double,
        taskWeight: Double,
    ): Double = dueness * roomWeight * taskWeight

    /** Highest P first; ties broken by [label] so the order is stable between recompositions. */
    fun <T> orderByPriority(
        items: List<T>,
        priority: (T) -> Double,
        label: (T) -> String,
    ): List<T> = items.sortedWith(compareByDescending(priority).thenBy(label))

    fun clampTau(tauDays: Double): Double = tauDays.coerceIn(Tuning.TAU_DAYS_MIN, Tuning.TAU_DAYS_MAX)

    /**
     * Snoozed SNOOZE_STREAK times in a row: tau <- 1.15 tau.
     *
     * [snoozeStreak] counts snoozes since the last completion, including the one just logged.
     * The factor applies each time the streak reaches a multiple of 3 (3, 6, 9, ...), so a
     * long run of snoozes keeps lengthening the interval in steps instead of every tap.
     */
    fun tauAfterSnooze(
        tauDays: Double,
        snoozeStreak: Int,
    ): Double =
        if (snoozeStreak > 0 && snoozeStreak % Tuning.SNOOZE_STREAK == 0) {
            clampTau(tauDays * Tuning.TAU_FACTOR_SNOOZED)
        } else {
            tauDays
        }

    /**
     * Completed while D < 0.5 three times in a row: tau <- 0.9 tau.
     *
     * [recentDueness] holds D at completion for this task's completions, newest first,
     * including the one just logged (nulls, from before v2 recorded D, end the streak).
     * The streak is the run of leading values below 0.5; the factor applies when it reaches a
     * multiple of 3, mirroring [tauAfterSnooze].
     */
    fun tauAfterCompletion(
        tauDays: Double,
        recentDueness: List<Double?>,
    ): Double {
        val streak = recentDueness.takeWhile { it != null && it < Tuning.EAGER_DUENESS }.size
        return if (streak > 0 && streak % Tuning.EAGER_STREAK == 0) {
            clampTau(tauDays * Tuning.TAU_FACTOR_EAGER)
        } else {
            tauDays
        }
    }
}
