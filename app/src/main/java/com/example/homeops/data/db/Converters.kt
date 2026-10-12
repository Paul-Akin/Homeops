package com.example.homeops.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Room can only store primitives and strings. These converters teach it the rest.
 * Enums need no converter: Room stores them by `name` automatically.
 */
class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /** Stored as a JSON array so commas or newlines inside a supply name survive. */
    @TypeConverter
    fun stringListToJson(value: List<String>): String = Json.encodeToString(stringList, value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(stringList, value)

    private companion object {
        val stringList = ListSerializer(String.serializer())
    }
}
