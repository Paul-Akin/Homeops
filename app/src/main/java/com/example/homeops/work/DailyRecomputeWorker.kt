package com.example.homeops.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic (every 24 h) safety net (DESIGN.md Section 8). Due-ness D(t) is computed on read, so there
 * is nothing to persist; this re-learns the reminder time from recent completions and re-schedules the
 * next digest, in case the chain of one-shot digest jobs was ever broken. Badge evaluation joins in M6.
 */
@HiltWorker
class DailyRecomputeWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val scheduler: ReminderScheduler,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            scheduler.rescheduleDigest()
            return Result.success()
        }
    }
