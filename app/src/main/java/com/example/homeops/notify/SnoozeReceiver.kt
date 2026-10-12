package com.example.homeops.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.homeops.data.repo.TaskRepository
import com.example.homeops.domain.Tuning
import com.example.homeops.work.ReminderScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the "Snooze 3 h" notification action: logs a SnoozeEvent per task (which may lengthen
 * tau, DESIGN.md Section 5.2) and schedules a repeat reminder 3 h later, outside quiet hours.
 *
 * Receivers get dependencies through a Hilt EntryPoint (a lookup into the app's DI graph) rather
 * than field injection. `goAsync()` keeps the receiver alive while the database write finishes.
 */
class SnoozeReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun taskRepository(): TaskRepository

        fun reminderScheduler(): ReminderScheduler
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val taskIds = intent.getLongArrayExtra(EXTRA_TASK_IDS)?.toList().orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, ReminderNotifier.DIGEST_ID)
        NotificationManagerCompat.from(context).cancel(notificationId)

        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deps.taskRepository().snoozeTasks(taskIds, Tuning.SNOOZE_HOURS)
                deps.reminderScheduler().scheduleSnoozedDigest(Tuning.SNOOZE_HOURS.toLong())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_IDS = "task_ids"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
