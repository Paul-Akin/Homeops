package com.example.homeops.work

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.homeops.data.prefs.SettingsRepository
import com.example.homeops.data.repo.TaskRepository
import com.example.homeops.domain.Tuning
import com.example.homeops.domain.scheduling.NotificationTimeLearner
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Decides *when* reminders fire and hands that to WorkManager (DESIGN.md Section 8).
 *
 * WorkManager runs deferred background jobs that survive app restarts and reboots. Each job here is
 * "unique work" with a fixed name, so re-scheduling replaces the pending job instead of piling up
 * duplicates. The math for the time itself lives in the domain's NotificationTimeLearner.
 */
@Singleton
class ReminderScheduler
    @Inject
    constructor(
        /**
         * A Provider, not WorkManager itself: WorkManager initializes by asking HomeOpsApplication for
         * its Hilt worker factory, so it must not be created while Hilt is still injecting that app.
         */
        private val workManagerProvider: Provider<WorkManager>,
        private val tasks: TaskRepository,
        private val settings: SettingsRepository,
        private val clock: Clock,
    ) {
        /** Next digest time and how it was chosen, for the Settings screen. Null when reminders are off. */
        suspend fun preview(): Pair<ZonedDateTime, NotificationTimeLearner.ReminderTime>? {
            val s = settings.currentReminderSettings()
            if (!s.enabled) return null
            val now = ZonedDateTime.now(clock)
            val since = now.toInstant().minus(Duration.ofDays(Tuning.LEARN_LOOKBACK_DAYS))
            return NotificationTimeLearner.nextReminder(
                now = now,
                completions = tasks.completionTimesSince(since),
                settings = s,
                digestSentToday = settings.digestSentOn(now.toLocalDate()),
            )
        }

        /**
         * (Re)schedules the next daily digest, or cancels everything if reminders are off.
         * [fromWorker] = true when called by the digest job itself: then the new job is appended after
         * the running one, because REPLACE would cancel the job that is calling us.
         */
        suspend fun rescheduleDigest(fromWorker: Boolean = false) {
            val next = preview()
            if (next == null) {
                cancelAll()
                return
            }
            val delay = Duration.between(ZonedDateTime.now(clock), next.first).coerceAtLeast(Duration.ZERO)
            val request =
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(ReminderWorker.KEY_MODE to ReminderWorker.MODE_DIGEST))
                    .build()
            val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
            workManagerProvider.get().enqueueUniqueWork(WORK_DIGEST, policy, request)
        }

        /** Repeat the digest [hours] from now, pushed out of quiet hours. */
        suspend fun scheduleSnoozedDigest(hours: Long) {
            val s = settings.currentReminderSettings()
            if (!s.enabled) return
            val now = ZonedDateTime.now(clock)
            val at = NotificationTimeLearner.nextAllowed(now.plusHours(hours), s)
            enqueueOneShot(WORK_SNOOZE, ReminderWorker.MODE_SNOOZE, Duration.between(now, at))
        }

        /** The day's single overdue nudge, a few hours after the digest. Dropped if it lands in quiet hours. */
        fun scheduleOverdueNudge() {
            enqueueOneShot(
                WORK_OVERDUE,
                ReminderWorker.MODE_OVERDUE,
                Duration.ofHours(Tuning.OVERDUE_NUDGE_DELAY_HOURS),
            )
        }

        /** Daily safety net that re-derives the next digest time; KEEP leaves an existing schedule alone. */
        fun ensureDailyRecompute() {
            val request = PeriodicWorkRequestBuilder<DailyRecomputeWorker>(1, TimeUnit.DAYS).build()
            workManagerProvider.get().enqueueUniquePeriodicWork(WORK_DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun enqueueOneShot(
            name: String,
            mode: String,
            delay: Duration,
        ) {
            val request =
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay.coerceAtLeast(Duration.ZERO).toMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(ReminderWorker.KEY_MODE to mode))
                    .build()
            workManagerProvider.get().enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        }

        private fun cancelAll() {
            workManagerProvider.get().cancelUniqueWork(WORK_DIGEST)
            workManagerProvider.get().cancelUniqueWork(WORK_SNOOZE)
            workManagerProvider.get().cancelUniqueWork(WORK_OVERDUE)
        }

        companion object {
            const val WORK_DIGEST = "reminder_digest"
            const val WORK_SNOOZE = "reminder_snooze"
            const val WORK_OVERDUE = "reminder_overdue"
            const val WORK_DAILY = "daily_recompute"
        }
    }
