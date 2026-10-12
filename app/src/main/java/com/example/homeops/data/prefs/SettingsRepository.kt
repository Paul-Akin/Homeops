package com.example.homeops.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.homeops.domain.Tuning
import com.example.homeops.domain.model.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings and small bookkeeping values in Jetpack DataStore (a key-value file, the modern
 * replacement for SharedPreferences). Reads are Flows, so the UI updates when a value changes.
 */
@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) {
        val reminderSettings: Flow<ReminderSettings> =
            store.data.map { it.toReminderSettings() }

        suspend fun currentReminderSettings(): ReminderSettings = reminderSettings.first()

        suspend fun updateReminderSettings(transform: (ReminderSettings) -> ReminderSettings) {
            store.edit { p ->
                val next = transform(p.toReminderSettings())
                p[ENABLED] = next.enabled
                p[DEFAULT_MINUTE] = Math.floorMod(next.defaultMinute, MINUTES_PER_DAY)
                p[QUIET_START] = Math.floorMod(next.quietStartMinute, MINUTES_PER_DAY)
                p[QUIET_END] = Math.floorMod(next.quietEndMinute, MINUTES_PER_DAY)
                p[SPLIT_WEEKEND] = next.splitWeekdayWeekend
            }
        }

        // "At most one digest per day plus at most one overdue nudge" (DESIGN.md Section 5.3).

        suspend fun digestSentOn(date: LocalDate): Boolean = store.data.first()[LAST_DIGEST_DATE] == date.toString()

        suspend fun markDigestSent(date: LocalDate) {
            store.edit { it[LAST_DIGEST_DATE] = date.toString() }
        }

        suspend fun nudgeSentOn(date: LocalDate): Boolean = store.data.first()[LAST_NUDGE_DATE] == date.toString()

        suspend fun markNudgeSent(date: LocalDate) {
            store.edit { it[LAST_NUDGE_DATE] = date.toString() }
        }

        private fun Preferences.toReminderSettings() =
            ReminderSettings(
                enabled = this[ENABLED] ?: false,
                defaultMinute = this[DEFAULT_MINUTE] ?: Tuning.DEFAULT_REMINDER_MINUTE,
                quietStartMinute = this[QUIET_START] ?: Tuning.DEFAULT_QUIET_START_MINUTE,
                quietEndMinute = this[QUIET_END] ?: Tuning.DEFAULT_QUIET_END_MINUTE,
                splitWeekdayWeekend = this[SPLIT_WEEKEND] ?: true,
            )

        private companion object {
            const val MINUTES_PER_DAY = 24 * 60
            val ENABLED = booleanPreferencesKey("reminders_enabled")
            val DEFAULT_MINUTE = intPreferencesKey("reminder_default_minute")
            val QUIET_START = intPreferencesKey("quiet_start_minute")
            val QUIET_END = intPreferencesKey("quiet_end_minute")
            val SPLIT_WEEKEND = booleanPreferencesKey("split_weekday_weekend")
            val LAST_DIGEST_DATE = stringPreferencesKey("last_digest_date")
            val LAST_NUDGE_DATE = stringPreferencesKey("last_nudge_date")
        }
    }
