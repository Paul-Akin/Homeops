package com.example.homeops.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.homeops.data.prefs.SettingsRepository
import com.example.homeops.domain.scheduling.NotificationTimeLearner
import com.example.homeops.notify.ReminderNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.ZonedDateTime

/**
 * Posts one reminder notification (DESIGN.md Section 8). The input "mode" says which:
 *  - DIGEST: the once-a-day "N tasks due" summary, then schedules tomorrow's and the overdue nudge.
 *  - SNOOZE: the digest again, 3 h after the user tapped Snooze (does not count as a new day's digest).
 *  - OVERDUE: the day's single low-priority overdue nudge.
 *
 * `@HiltWorker` + `@AssistedInject`: WorkManager supplies context and params at run time ("assisted"),
 * Hilt supplies everything else.
 */
@HiltWorker
class ReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val notifier: ReminderNotifier,
        private val scheduler: ReminderScheduler,
        private val settings: SettingsRepository,
        private val clock: Clock,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val s = settings.currentReminderSettings()
            if (!s.enabled) return Result.success()
            val now = ZonedDateTime.now(clock)
            val today = now.toLocalDate()

            when (inputData.getString(KEY_MODE)) {
                MODE_DIGEST -> {
                    if (!settings.digestSentOn(today)) {
                        val posted = notifier.postDigest()
                        settings.markDigestSent(today)
                        if (posted) scheduler.scheduleOverdueNudge()
                    }
                    scheduler.rescheduleDigest(fromWorker = true)
                }

                MODE_SNOOZE -> {
                    notifier.postDigest()
                }

                MODE_OVERDUE -> {
                    if (!settings.nudgeSentOn(today) && !NotificationTimeLearner.isQuiet(now, s)) {
                        if (notifier.postOverdueNudge()) settings.markNudgeSent(today)
                    }
                }
            }
            return Result.success()
        }

        companion object {
            const val KEY_MODE = "mode"
            const val MODE_DIGEST = "digest"
            const val MODE_SNOOZE = "snooze"
            const val MODE_OVERDUE = "overdue"
        }
    }
