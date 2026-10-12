package com.example.homeops.notify

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.example.homeops.data.db.RoomEntity
import com.example.homeops.data.repo.TaskDraft
import com.example.homeops.domain.model.RoomType
import com.example.homeops.domain.model.TaskType
import com.example.homeops.testing.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

/**
 * M2 acceptance: a task with 1-day frequency produces a notification the next day.
 * (When it fires is NotificationTimeLearnerTest's job; this checks what gets posted.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderNotifierTest {
    private val zone = java.time.ZoneId.of("America/New_York")
    private val day0 = ZonedDateTime.of(2026, 10, 12, 9, 0, 0, 0, zone)
    private lateinit var env: TestEnv
    private lateinit var notifier: ReminderNotifier
    private lateinit var manager: NotificationManager
    private var taskId = 0L

    @Before
    fun setUp() =
        runTest {
            env = TestEnv(day0.toInstant())
            shadowOf(env.context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            NotificationChannels.createAll(env.context)
            manager = env.context.getSystemService(NotificationManager::class.java)
            notifier = ReminderNotifier(env.context, env.tasks, env.clock)

            val room = env.home.saveRoom(RoomEntity(name = "Kitchen", roomType = RoomType.KITCHEN))
            taskId =
                env.tasks.saveTask(
                    TaskDraft(
                        roomId = room,
                        zoneId = null,
                        title = "Wipe counters",
                        taskType = TaskType.WIPE,
                        areaFt2 = null,
                        severity = 3,
                        frequencyDays = 1.0,
                    ),
                )
            env.tasks.completeTask(taskId, day0.toInstant())
        }

    @After
    fun tearDown() = env.close()

    @Test
    fun dailyTaskIsInTheNextDaysDigest() =
        runTest {
            // Same day, 1 h after completing: D = 1 - 5^(-1/24) = 0.07, not due, nothing posted.
            env.clock.now = day0.plusHours(1).toInstant()
            assertFalse(notifier.postDigest())
            assertEquals(0, shadowOf(manager).allNotifications.size)

            // Next day 6:00 PM: t = 33 h, D = 1 - 5^(-33/24) = 0.89 >= 0.8, due.
            env.clock.now = day0.plusDays(1).withHour(18).toInstant()
            assertTrue(notifier.postDigest())

            val posted = shadowOf(manager).allNotifications.single()
            val extras = posted.extras
            // t0 for a default WIPE = 1 + 0.25 * 15 * 1.0 = 4.75 min, shown rounded.
            assertEquals("1 task due, about 5 min", extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
            assertEquals("Wipe counters", extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
            assertEquals(NotificationChannels.DUE_DIGEST, posted.channelId)
            assertEquals(listOf("Open", "Snooze 3 h"), posted.actions.map { it.title.toString() })
        }

    @Test
    fun overdueNudgeOnlyWhenPastTheOnTimeLimit() =
        runTest {
            env.clock.now = day0.plusDays(1).withHour(18).toInstant() // D = 0.89, due but not overdue
            assertFalse(notifier.postOverdueNudge())
            env.clock.now = day0.plusDays(2).toInstant() // D = 1 - 5^(-2) = 0.96 > 0.9
            assertTrue(notifier.postOverdueNudge())
            val nudge = shadowOf(manager).allNotifications.single()
            assertEquals(NotificationChannels.OVERDUE, nudge.channelId)
            assertTrue(nudge.actions.none { it.title.toString().startsWith("Snooze") })
        }

    @Test
    fun nothingPostedWithoutPermission() =
        runTest {
            shadowOf(env.context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            env.clock.now = day0.plusDays(2).toInstant()
            assertFalse(notifier.postDigest())
        }
}
