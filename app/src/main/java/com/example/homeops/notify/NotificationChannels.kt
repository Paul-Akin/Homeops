package com.example.homeops.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * Notification channels (DESIGN.md Section 8). Android 8+ requires every notification to belong to
 * a channel; the user can mute each channel separately in system settings. Creating an existing
 * channel again is a no-op, so this is safe to call on every app start.
 */
object NotificationChannels {
    const val DUE_DIGEST = "due_digest"
    const val OVERDUE = "overdue"
    const val BADGES = "badges"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(DUE_DIGEST, "Daily digest", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Once a day: which tasks are due and how long they should take"
                },
                NotificationChannel(OVERDUE, "Overdue nudges", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "At most one gentle nudge a day about overdue tasks"
                },
                NotificationChannel(BADGES, "Badges", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Achievement badges"
                },
            ),
        )
    }
}
