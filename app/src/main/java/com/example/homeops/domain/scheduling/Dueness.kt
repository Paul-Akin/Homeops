package com.example.homeops.domain.scheduling

import com.example.homeops.domain.Tuning
import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln

/**
 * The parts of the DESIGN.md Section 5.2 decay model that M1 needs.
 *
 * "Need to clean" accumulates as D(t) = 1 - exp(-t / tau), t in days since the last completion.
 * The due threshold D* is reached at t* = -tau * ln(1 - D*), which is tau * ln 5 for D* = 0.8.
 * So a nominal frequency of F days maps to tau = F / ln 5.
 *
 * Priority weighting, tau adaptation, and reminders arrive in M2 (DecayScheduler).
 */
object Dueness {
    private const val MILLIS_PER_DAY = 86_400_000.0

    /** -ln(1 - D*). Equals ln 5 = 1.6094 for D* = 0.8. */
    private val dueIntervalFactor: Double = -ln(1.0 - Tuning.DUE_THRESHOLD)

    /** tau = F / ln 5 (after clamping F to the allowed frequency range). */
    fun tauFromFrequencyDays(frequencyDays: Double): Double {
        require(frequencyDays.isFinite()) { "frequencyDays must be finite" }
        val f = frequencyDays.coerceIn(Tuning.FREQUENCY_DAYS_MIN, Tuning.FREQUENCY_DAYS_MAX)
        return f / dueIntervalFactor
    }

    /** Inverse of [tauFromFrequencyDays]: F = tau * ln 5. Used to show/edit a task's frequency. */
    fun frequencyDaysFromTau(tauDays: Double): Double {
        require(tauDays > 0.0) { "tauDays must be positive" }
        return tauDays * dueIntervalFactor
    }

    /** Elapsed days from [from] to [to], floored at 0 so clock skew never gives negative time. */
    fun daysBetween(
        from: Instant,
        to: Instant,
    ): Double = (Duration.between(from, to).toMillis() / MILLIS_PER_DAY).coerceAtLeast(0.0)

    /** D(t) = 1 - exp(-t / tau). Returns 0 at t = 0 and approaches 1 as t grows. */
    fun dueness(
        daysSince: Double,
        tauDays: Double,
    ): Double {
        require(tauDays > 0.0) { "tauDays must be positive" }
        return 1.0 - exp(-daysSince.coerceAtLeast(0.0) / tauDays)
    }

    /**
     * Due-ness of a task right now. Never-completed tasks (lastCompletedAt == null)
     * are treated as due immediately, D = 1 (DESIGN.md Section 5.2).
     */
    fun duenessAt(
        lastCompletedAt: Instant?,
        tauDays: Double,
        now: Instant,
    ): Double = if (lastCompletedAt == null) 1.0 else dueness(daysBetween(lastCompletedAt, now), tauDays)

    fun isDue(d: Double): Boolean = d >= Tuning.DUE_THRESHOLD

    /**
     * "On time" for the completion log: D <= 0.9 at the moment of completion (Section 5.5).
     *
     * Engineering judgment: for a task's first completion, t is measured from [createdAt]
     * rather than treating D as 1, otherwise every first completion would count as late.
     */
    fun wasOnTime(
        lastCompletedAt: Instant?,
        createdAt: Instant,
        tauDays: Double,
        completedAt: Instant,
    ): Boolean {
        val reference = lastCompletedAt ?: createdAt
        return dueness(daysBetween(reference, completedAt), tauDays) <= Tuning.ON_TIME_THRESHOLD
    }

    /**
     * M1 ordering for the Today list: highest D first, ties broken by [label] so the order is stable.
     * M2 replaces this with the weighted priority P = D * w_room * w_task.
     */
    fun <T> orderByDueness(
        items: List<T>,
        dueness: (T) -> Double,
        label: (T) -> String,
    ): List<T> = items.sortedWith(compareByDescending(dueness).thenBy(label))
}
