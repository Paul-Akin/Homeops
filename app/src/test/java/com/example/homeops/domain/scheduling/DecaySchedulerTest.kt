package com.example.homeops.domain.scheduling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DecaySchedulerTest {
    @Test
    fun `priority is the product D times room weight times task weight`() {
        assertEquals(0.8 * 1.5 * 2.0, DecayScheduler.priority(0.8, 1.5, 2.0), 1e-12)
        assertEquals(0.0, DecayScheduler.priority(0.0, 3.0, 3.0), 0.0)
    }

    @Test
    fun `ordering is by P descending with a stable label tiebreak`() {
        data class Row(
            val label: String,
            val d: Double,
            val w: Double,
        )
        val rows = listOf(Row("b", 0.5, 1.0), Row("a", 1.0, 1.0), Row("c", 0.6, 2.0), Row("a2", 0.5, 1.0))
        val ordered = DecayScheduler.orderByPriority(rows, { DecayScheduler.priority(it.d, it.w, 1.0) }, { it.label })
        // P: c = 1.2, a = 1.0, a2 = b = 0.5
        assertEquals(listOf("c", "a", "a2", "b"), ordered.map { it.label })
    }

    @Test
    fun `third consecutive snooze lengthens tau by 15 percent`() {
        assertEquals(4.0, DecayScheduler.tauAfterSnooze(4.0, 1), 0.0)
        assertEquals(4.0, DecayScheduler.tauAfterSnooze(4.0, 2), 0.0)
        assertEquals(4.6, DecayScheduler.tauAfterSnooze(4.0, 3), 1e-12)
        assertEquals(4.0, DecayScheduler.tauAfterSnooze(4.0, 4), 0.0)
        assertEquals(4.6, DecayScheduler.tauAfterSnooze(4.0, 6), 1e-12)
        assertEquals(4.0, DecayScheduler.tauAfterSnooze(4.0, 0), 0.0)
    }

    @Test
    fun `three eager completions in a row shorten tau by 10 percent`() {
        assertEquals(3.6, DecayScheduler.tauAfterCompletion(4.0, listOf(0.3, 0.2, 0.4)), 1e-12)
        assertEquals(3.6, DecayScheduler.tauAfterCompletion(4.0, listOf(0.3, 0.2, 0.4, 0.9)), 1e-12)
        // Streak broken by a late completion, too short, or past the multiple of 3.
        assertEquals(4.0, DecayScheduler.tauAfterCompletion(4.0, listOf(0.3, 0.6, 0.2)), 0.0)
        assertEquals(4.0, DecayScheduler.tauAfterCompletion(4.0, listOf(0.3, 0.2)), 0.0)
        assertEquals(4.0, DecayScheduler.tauAfterCompletion(4.0, listOf(0.1, 0.1, 0.1, 0.1)), 0.0)
        // Exactly 0.5 is not "below 0.5"; null (pre-v2 history) ends the streak.
        assertEquals(4.0, DecayScheduler.tauAfterCompletion(4.0, listOf(0.3, 0.2, 0.5)), 0.0)
        assertEquals(4.0, DecayScheduler.tauAfterCompletion(4.0, listOf(0.3, 0.2, null)), 0.0)
        assertEquals(4.0, DecayScheduler.tauAfterCompletion(4.0, emptyList()), 0.0)
    }

    @Test
    fun `adapted tau stays within 0_5 to 180 days`() {
        assertEquals(180.0, DecayScheduler.tauAfterSnooze(170.0, 3), 0.0)
        assertEquals(0.5, DecayScheduler.tauAfterCompletion(0.52, listOf(0.1, 0.1, 0.1)), 0.0)
        assertEquals(0.5, DecayScheduler.clampTau(0.01), 0.0)
    }
}
