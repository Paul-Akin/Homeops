package com.example.homeops.domain.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FormValidationTest {
    private fun <T> FieldResult<T>.ok(): T = (this as FieldResult.Ok<T>).value

    private fun FieldResult<*>.isError(): Boolean = this is FieldResult.Error

    @Test
    fun `name is trimmed, required, and length-limited`() {
        assertEquals("Kitchen", FormValidation.name("  Kitchen ").ok())
        assertTrue(FormValidation.name("   ").isError())
        assertTrue(FormValidation.name("x".repeat(81)).isError())
        assertEquals(80, FormValidation.name("x".repeat(80)).ok().length)
    }

    @Test
    fun `area is optional and range-checked`() {
        assertNull(FormValidation.optionalAreaFt2("").ok())
        assertEquals(12.5, FormValidation.optionalAreaFt2(" 12.5 ").ok())
        assertTrue(FormValidation.optionalAreaFt2("0.1").isError())
        assertTrue(FormValidation.optionalAreaFt2("5000").isError())
        assertTrue(FormValidation.optionalAreaFt2("abc").isError())
        assertTrue(FormValidation.optionalAreaFt2("NaN").isError())
        assertTrue(FormValidation.optionalAreaFt2("Infinity").isError())
    }

    @Test
    fun `frequency is required and within 1 to 365 days`() {
        assertEquals(7.0, FormValidation.frequencyDays("7").ok())
        assertEquals(1.0, FormValidation.frequencyDays("1").ok())
        assertEquals(365.0, FormValidation.frequencyDays("365").ok())
        assertTrue(FormValidation.frequencyDays("").isError())
        assertTrue(FormValidation.frequencyDays("0.5").isError())
        assertTrue(FormValidation.frequencyDays("400").isError())
        assertEquals("Frequency must be 1 to 365 days", FormValidation.frequencyDays("0").errorOrNull)
    }

    @Test
    fun `room weight defaults to 1 and is bounded`() {
        assertEquals(1.0, FormValidation.roomWeight("").ok())
        assertEquals(2.5, FormValidation.roomWeight("2.5").ok())
        assertTrue(FormValidation.roomWeight("0").isError())
        assertTrue(FormValidation.roomWeight("9").isError())
    }

    @Test
    fun `floor defaults to 1 and must be a whole number`() {
        assertEquals(1, FormValidation.floor("").ok())
        assertEquals(-1, FormValidation.floor("-1").ok())
        assertTrue(FormValidation.floor("1.5").isError())
        assertTrue(FormValidation.floor("-10").isError())
    }

    @Test
    fun `severity is clamped to 1 to 5`() {
        assertEquals(1, FormValidation.severity(0))
        assertEquals(3, FormValidation.severity(3))
        assertEquals(5, FormValidation.severity(8))
    }

    @Test
    fun `task weight defaults to 1 and is bounded`() {
        assertEquals(1.0, FormValidation.taskWeight("").ok())
        assertEquals(0.5, FormValidation.taskWeight("0.5").ok())
        assertTrue(FormValidation.taskWeight("0.05").isError())
        assertTrue(FormValidation.taskWeight("6").isError())
        assertTrue(FormValidation.taskWeight("x").isError())
    }
}
