package com.example.homeops.data.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class ConvertersTest {
    private val c = Converters()

    @Test
    fun `instant round trips through epoch millis`() {
        val t = Instant.parse("2026-03-14T15:09:26.535Z")
        assertEquals(1773500966535L, c.instantToEpochMillis(t))
        assertEquals(t, c.epochMillisToInstant(c.instantToEpochMillis(t)))
        assertNull(c.instantToEpochMillis(null))
        assertNull(c.epochMillisToInstant(null))
    }

    @Test
    fun `string lists survive commas, quotes, and newlines`() {
        val supplies = listOf("degreaser", "cloth, microfiber", "say \"hi\"", "line1\nline2", "")
        assertEquals(supplies, c.jsonToStringList(c.stringListToJson(supplies)))
        assertEquals(emptyList<String>(), c.jsonToStringList(c.stringListToJson(emptyList())))
        assertEquals(emptyList<String>(), c.jsonToStringList(""))
    }
}
