package com.example.homeops.domain.estimation

import com.example.homeops.domain.Tuning
import com.example.homeops.domain.model.TaskType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PriorEstimateTest {
    @Test
    fun `severity factor spans 0_6 to 1_4 and clamps out-of-range input`() {
        assertEquals(0.6, PriorEstimate.severityFactor(1), 1e-12)
        assertEquals(1.0, PriorEstimate.severityFactor(3), 1e-12)
        assertEquals(1.4, PriorEstimate.severityFactor(5), 1e-12)
        assertEquals(0.6, PriorEstimate.severityFactor(-7), 1e-12)
        assertEquals(1.4, PriorEstimate.severityFactor(99), 1e-12)
    }

    // Hand checks of t0 = a + b * A * s against the DESIGN.md Section 5.1 table.
    @Test
    fun `vacuum default area at nominal severity`() {
        // 3 + 0.04 * 150 * 1.0 = 9.0 min
        assertEquals(9.0, PriorEstimate.priorMinutes(TaskType.VACUUM, null, 3), 1e-9)
    }

    @Test
    fun `scrub with explicit area at max severity`() {
        // 3 + 0.6 * 20 * 1.4 = 19.8 min
        assertEquals(19.8, PriorEstimate.priorMinutes(TaskType.SCRUB, 20.0, 5), 1e-9)
    }

    @Test
    fun `mop at min severity`() {
        // 5 + 0.06 * 100 * 0.6 = 8.6 min
        assertEquals(8.6, PriorEstimate.priorMinutes(TaskType.MOP, null, 1), 1e-9)
    }

    @Test
    fun `area-independent tasks are pure setup time`() {
        assertEquals(10.0, PriorEstimate.priorMinutes(TaskType.LAUNDRY, 500.0, 5), 1e-9)
        assertEquals(5.0, PriorEstimate.priorMinutes(TaskType.DISHES, null, 1), 1e-9)
        assertEquals(3.0, PriorEstimate.priorMinutes(TaskType.TRASH, null, 3), 1e-9)
    }

    @Test
    fun `every task type has a table row and a positive prior`() {
        for (type in TaskType.entries) {
            assertTrue(Tuning.priorTable.containsKey(type), "missing row for $type")
            assertTrue(PriorEstimate.priorMinutes(type, null, 3) > 0.0)
        }
    }

    @Test
    fun `negative or non-finite area is rejected`() {
        assertThrows<IllegalArgumentException> { PriorEstimate.priorMinutes(TaskType.WIPE, -1.0, 3) }
        assertThrows<IllegalArgumentException> { PriorEstimate.priorMinutes(TaskType.WIPE, Double.NaN, 3) }
        assertThrows<IllegalArgumentException> {
            PriorEstimate.priorMinutes(TaskType.WIPE, Double.POSITIVE_INFINITY, 3)
        }
    }
}
