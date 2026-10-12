package com.example.homeops.domain.scheduling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DigestTest {
    private fun item(
        title: String,
        min: Double,
        d: Double = 0.85,
        p: Double = d,
    ) = DigestItem(title, min, d, p)

    @Test
    fun `digest headline counts tasks and sums minutes`() {
        val text =
            Digest.digest(
                listOf(item("Mop", 8.6), item("Vacuum", 9.0), item("Wipe", 4.75), item("Trash", 3.0)),
            )!!
        assertEquals("4 tasks due, about 25 min", text.title) // 25.35 rounds to 25
    }

    @Test
    fun `body lists the top three by priority then a count`() {
        val text =
            Digest.digest(
                listOf(
                    item("A", 1.0, p = 0.1),
                    item("B", 1.0, p = 0.9),
                    item("C", 1.0, p = 0.5),
                    item("D", 1.0, p = 0.7),
                ),
            )!!
        assertEquals("B, D, C, and 1 more", text.body)
    }

    @Test
    fun `singular wording and empty input`() {
        assertEquals("1 task due, about 9 min", Digest.digest(listOf(item("Vacuum", 9.0)))!!.title)
        assertNull(Digest.digest(emptyList()))
    }

    @Test
    fun `overdue nudge includes only D above 0_9`() {
        val text = Digest.overdueNudge(listOf(item("Late", 5.0, d = 0.95), item("Due", 5.0, d = 0.85)))!!
        assertEquals("1 task is overdue", text.title)
        assertEquals("Late", text.body)
        assertNull(Digest.overdueNudge(listOf(item("Due", 5.0, d = 0.9))))
    }
}
