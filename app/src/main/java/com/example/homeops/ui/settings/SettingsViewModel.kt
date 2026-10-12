package com.example.homeops.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homeops.data.prefs.SettingsRepository
import com.example.homeops.domain.model.ReminderSettings
import com.example.homeops.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val reminders: ReminderSettings = ReminderSettings(),
    /** When the next digest will fire; null when reminders are off. */
    val nextReminder: ZonedDateTime? = null,
    /** True if the time was learned from completions (N >= 5, R >= 0.5), false if it is the default. */
    val learned: Boolean = false,
    val samples: Int = 0,
    val consistency: Double = 0.0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val scheduler: ReminderScheduler,
    ) : ViewModel() {
        // mapLatest: whenever settings change, recompute the preview (cancelling a stale computation).
        val state: StateFlow<SettingsUiState> =
            settings.reminderSettings
                .mapLatest { s ->
                    val preview = scheduler.preview()
                    SettingsUiState(
                        isLoading = false,
                        reminders = s,
                        nextReminder = preview?.first,
                        learned = preview?.second?.learned ?: false,
                        samples = preview?.second?.stats?.n ?: 0,
                        consistency = preview?.second?.stats?.r ?: 0.0,
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

        fun setDefaultMinute(minute: Int) = update { it.copy(defaultMinute = minute) }

        fun setQuietStart(minute: Int) = update { it.copy(quietStartMinute = minute) }

        fun setQuietEnd(minute: Int) = update { it.copy(quietEndMinute = minute) }

        fun setSplitWeekdayWeekend(split: Boolean) = update { it.copy(splitWeekdayWeekend = split) }

        /** Save, then re-plan the next digest (or cancel reminders when turned off). */
        private fun update(transform: (ReminderSettings) -> ReminderSettings) {
            viewModelScope.launch {
                settings.updateReminderSettings(transform)
                scheduler.rescheduleDigest()
            }
        }
    }
