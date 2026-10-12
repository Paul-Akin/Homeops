package com.example.homeops.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.homeops.MainActivity
import com.example.homeops.R
import com.example.homeops.data.db.TaskWithPlace
import com.example.homeops.data.repo.TaskRepository
import com.example.homeops.domain.Tuning
import com.example.homeops.domain.scheduling.DecayScheduler
import com.example.homeops.domain.scheduling.Digest
import com.example.homeops.domain.scheduling.DigestItem
import com.example.homeops.domain.scheduling.Dueness
import com.example.homeops.domain.scheduling.NotificationText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts the reminder notifications. The wording comes from the domain [Digest];
 * scheduling (when to call this) lives in work/ReminderScheduler.
 */
@Singleton
class ReminderNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val tasks: TaskRepository,
        private val clock: Clock,
    ) {
        /** Posts "N tasks due, about M min" if anything is due. Returns true if a notification was posted. */
        suspend fun postDigest(): Boolean {
            val due = dueNow()
            val text = Digest.digest(due.map { it.second }) ?: return false
            val taskIds = due.map { it.first.task.id }.toLongArray()
            return post(DIGEST_ID, NotificationChannels.DUE_DIGEST, text, snoozeTaskIds = taskIds)
        }

        /** Posts the overdue nudge if any task has D > 0.9. Returns true if posted. */
        suspend fun postOverdueNudge(): Boolean {
            val text = Digest.overdueNudge(dueNow().map { it.second }) ?: return false
            return post(OVERDUE_ID, NotificationChannels.OVERDUE, text, snoozeTaskIds = null)
        }

        private suspend fun dueNow(): List<Pair<TaskWithPlace, DigestItem>> {
            val now = clock.instant()
            return tasks.activeTasksNow().mapNotNull { row ->
                val d = Dueness.duenessAt(row.task.lastCompletedAt, row.task.tauDays, now)
                if (!Dueness.isDue(d)) return@mapNotNull null
                val p = DecayScheduler.priority(d, row.roomWeight, row.task.weight)
                row to DigestItem(row.task.title, row.task.estMinutesPrior, d, p)
            }
        }

        private fun post(
            id: Int,
            channel: String,
            text: NotificationText,
            snoozeTaskIds: LongArray?,
        ): Boolean {
            // Android 13+ needs the runtime POST_NOTIFICATIONS grant; the user can also block the app.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return false

            val builder =
                NotificationCompat
                    .Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(text.title)
                    .setContentText(text.body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
                    .setContentIntent(openAppIntent())
                    .setAutoCancel(true)
                    .addAction(0, "Open", openAppIntent())
            if (snoozeTaskIds != null) {
                builder.addAction(0, "Snooze ${Tuning.SNOOZE_HOURS} h", snoozeIntent(id, snoozeTaskIds))
            }
            manager.notify(id, builder.build())
            return true
        }

        private fun openAppIntent(): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        private fun snoozeIntent(
            notificationId: Int,
            taskIds: LongArray,
        ): PendingIntent {
            val intent =
                Intent(context, SnoozeReceiver::class.java)
                    .putExtra(SnoozeReceiver.EXTRA_TASK_IDS, taskIds)
                    .putExtra(SnoozeReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            return PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        companion object {
            const val DIGEST_ID = 1001
            const val OVERDUE_ID = 1002
        }
    }
