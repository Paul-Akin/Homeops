package com.example.homeops.domain.validation

import com.example.homeops.domain.Tuning

/**
 * Result of parsing one form field. A `sealed interface` is a closed set of cases, so a
 * `when (result)` over it must handle both Ok and Error (the compiler checks this).
 */
sealed interface FieldResult<out T> {
    data class Ok<T>(val value: T) : FieldResult<T>

    data class Error(val message: String) : FieldResult<Nothing>
}

val FieldResult<*>.errorOrNull: String? get() = (this as? FieldResult.Error)?.message

/**
 * Parsing and range checks for the M1 CRUD forms. Pure functions of the raw text so the
 * UI stays dumb and the rules are unit-tested. Ranges live in [Tuning].
 */
object FormValidation {

    fun name(raw: String): FieldResult<String> {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> FieldResult.Error("Name is required")
            trimmed.length > Tuning.NAME_MAX_LENGTH -> FieldResult.Error("At most ${Tuning.NAME_MAX_LENGTH} characters")
            else -> FieldResult.Ok(trimmed)
        }
    }

    /** Optional area in ft². Blank means "unknown" (null), which falls back to the type default. */
    fun optionalAreaFt2(raw: String): FieldResult<Double?> {
        if (raw.isBlank()) return FieldResult.Ok(null)
        return when (val v = parseFiniteDouble(raw)) {
            null -> FieldResult.Error("Enter a number")
            !in Tuning.AREA_FT2_MIN..Tuning.AREA_FT2_MAX ->
                FieldResult.Error("Area must be ${fmt(Tuning.AREA_FT2_MIN)} to ${fmt(Tuning.AREA_FT2_MAX)} ft²")
            else -> FieldResult.Ok(v)
        }
    }

    /** Required nominal frequency in days, 1..365 (DESIGN.md Section 6.4 range). */
    fun frequencyDays(raw: String): FieldResult<Double> =
        when (val v = parseFiniteDouble(raw)) {
            null -> FieldResult.Error("Enter a number of days")
            !in Tuning.FREQUENCY_DAYS_MIN..Tuning.FREQUENCY_DAYS_MAX ->
                FieldResult.Error(
                    "Frequency must be ${fmt(Tuning.FREQUENCY_DAYS_MIN)} to ${fmt(Tuning.FREQUENCY_DAYS_MAX)} days",
                )
            else -> FieldResult.Ok(v)
        }

    /** Room priority multiplier w_room. Blank means the default 1.0. */
    fun roomWeight(raw: String): FieldResult<Double> {
        if (raw.isBlank()) return FieldResult.Ok(1.0)
        return when (val v = parseFiniteDouble(raw)) {
            null -> FieldResult.Error("Enter a number")
            !in Tuning.ROOM_WEIGHT_MIN..Tuning.ROOM_WEIGHT_MAX ->
                FieldResult.Error("Weight must be ${fmt(Tuning.ROOM_WEIGHT_MIN)} to ${fmt(Tuning.ROOM_WEIGHT_MAX)}")
            else -> FieldResult.Ok(v)
        }
    }

    fun floor(raw: String): FieldResult<Int> {
        if (raw.isBlank()) return FieldResult.Ok(1)
        return when (val v = raw.trim().toIntOrNull()) {
            null -> FieldResult.Error("Enter a whole number")
            !in Tuning.FLOOR_MIN..Tuning.FLOOR_MAX ->
                FieldResult.Error("Floor must be ${Tuning.FLOOR_MIN} to ${Tuning.FLOOR_MAX}")
            else -> FieldResult.Ok(v)
        }
    }

    fun severity(value: Int): Int = value.coerceIn(Tuning.SEVERITY_MIN, Tuning.SEVERITY_MAX)

    /** Accepts "12", "12.5", " 12 "; rejects NaN/Infinity, which toDoubleOrNull() would accept. */
    private fun parseFiniteDouble(raw: String): Double? = raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun fmt(x: Double): String = if (x == Math.floor(x)) x.toLong().toString() else x.toString()
}
