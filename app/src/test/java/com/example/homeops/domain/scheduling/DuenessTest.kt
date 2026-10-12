package com.example.homeops.domain.scheduling

import com.example.homeops.domain.Tuning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import kotlin.math.ln

class DuenessTest {
    private val t0: Instant = Instant.parse("2026-01-01T12:00:00Z")

    private fun daysLater(days: Double): Instant = t0.plusMillis((days * 86_400_000).toLong())

    // DESIGN.md Section 10 fixture: tau = 7 / ln 5 = 4.349 days; at t = 7, D = 0.800.
    @Test
    fun `weekly task fixture - tau and D at one period`() {
        val tau = Dueness.tauFromFrequencyDays(7.0)
        assertEquals(4.349, tau, 5e-4)
        assertEquals(7.0 / ln(5.0), tau, 1e-12)
        assertEquals(0.800, Dueness.dueness(7.0, tau), 1e-9)
        assertTrue(Dueness.isDue(Dueness.dueness(7.0, tau)))
        assertFalse(Dueness.isDue(Dueness.dueness(6.9, tau)))
    }

    @Test
    fun `frequency and tau round trip`() {
        for (f in listOf(1.0, 3.5, 7.0, 30.0, 365.0)) {
            assertEquals(f, Dueness.frequencyDaysFromTau(Dueness.tauFromFrequencyDays(f)), 1e-9)
        }
    }

    @Test
    fun `frequency is clamped to 1 to 365 days`() {
        assertEquals(Dueness.tauFromFrequencyDays(1.0), Dueness.tauFromFrequencyDays(0.1), 1e-12)
        assertEquals(Dueness.tauFromFrequencyDays(365.0), Dueness.tauFromFrequencyDays(1000.0), 1e-12)
        assertThrows<IllegalArgumentException> { Dueness.tauFromFrequencyDays(Double.NaN) }
    }

    @Test
    fun `D is 0 at completion, monotone, and bounded by 1`() {
        val tau = 4.0
        assertEquals(0.0, Dueness.dueness(0.0, tau), 0.0)
        var prev = 0.0
        for (t in 1..60) {
            val d = Dueness.dueness(t.toDouble(), tau)
            assertTrue(d > prev && d < 1.0 + 1e-15)
            prev = d
        }
        // One time constant: D = 1 - 1/e = 0.6321.
        assertEquals(0.63212, Dueness.dueness(tau, tau), 1e-5)
        // Negative elapsed time (clock skew) is treated as zero.
        assertEquals(0.0, Dueness.dueness(-2.0, tau), 0.0)
        assertThrows<IllegalArgumentException> { Dueness.dueness(1.0, 0.0) }
    }

    @Test
    fun `never-completed tasks are due immediately`() {
        assertEquals(1.0, Dueness.duenessAt(null, 4.0, t0), 0.0)
        assertTrue(Dueness.isDue(Dueness.duenessAt(null, 4.0, t0)))
    }

    @Test
    fun `duenessAt uses elapsed days between instants`() {
        val tau = Dueness.tauFromFrequencyDays(7.0)
        assertEquals(0.8, Dueness.duenessAt(t0, tau, daysLater(7.0)), 1e-6)
        assertEquals(1.5, Dueness.daysBetween(t0, t0.plus(Duration.ofHours(36))), 1e-12)
        assertEquals(0.0, Dueness.daysBetween(t0, t0.minus(Duration.ofHours(5))), 0.0)
    }

    @Test
    fun `on-time boundary is D = 0_9, i e t = tau ln 10`() {
        val tau = Dueness.tauFromFrequencyDays(7.0)
        // D = 0.9 at t = tau * ln(10) = 10.015 days for a weekly task.
        val tBoundary = tau * ln(1.0 / (1.0 - Tuning.ON_TIME_THRESHOLD))
        assertEquals(10.0145, tBoundary, 1e-3)
        assertTrue(Dueness.wasOnTime(t0, t0, tau, daysLater(tBoundary - 0.01)))
        assertFalse(Dueness.wasOnTime(t0, t0, tau, daysLater(tBoundary + 0.01)))
    }

    @Test
    fun `first completion is measured from createdAt`() {
        val tau = Dueness.tauFromFrequencyDays(7.0)
        assertTrue(Dueness.wasOnTime(null, t0, tau, daysLater(2.0)))
        assertFalse(Dueness.wasOnTime(null, t0, tau, daysLater(20.0)))
    }

    @Test
    fun `today ordering is by D descending with stable label tiebreak`() {
        data class Row(
            val label: String,
            val d: Double,
        )
        val rows = listOf(Row("b", 0.5), Row("a", 1.0), Row("c", 0.9), Row("a2", 0.5))
        val ordered = Dueness.orderByDueness(rows, { it.d }, { it.label })
        assertEquals(listOf("a", "c", "a2", "b"), ordered.map { it.label })
    }
}
