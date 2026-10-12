package com.example.homeops.data.repo

import android.app.Application
import com.example.homeops.data.db.RoomEntity
import com.example.homeops.domain.model.RoomType
import com.example.homeops.domain.model.TaskType
import com.example.homeops.domain.scheduling.Dueness
import com.example.homeops.testing.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/** Section 5.2 behavioral adaptation, end to end through the repository and database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AdaptationTest {
    private val start = Instant.parse("2026-01-05T09:00:00Z")
    private lateinit var env: TestEnv
    private var taskId = 0L
    private val tau7 = Dueness.tauFromFrequencyDays(7.0)

    @Before
    fun setUp() =
        runTest {
            env = TestEnv(start)
            val room = env.home.saveRoom(RoomEntity(name = "Kitchen", roomType = RoomType.KITCHEN))
            taskId =
                env.tasks.saveTask(
                    TaskDraft(
                        roomId = room,
                        zoneId = null,
                        title = "Wipe",
                        taskType = TaskType.WIPE,
                        areaFt2 = null,
                        severity = 3,
                        frequencyDays = 7.0,
                    ),
                )
        }

    @After
    fun tearDown() = env.close()

    private suspend fun tau() = env.tasks.getTask(taskId)!!.tauDays

    // M2 acceptance (second half): snooze logs an event.
    @Test
    fun snoozeLogsEventsAndThirdSnoozeLengthensTau() =
        runTest {
            env.tasks.snoozeTasks(listOf(taskId), hours = 3, at = start.plus(Duration.ofDays(8)))
            env.tasks.snoozeTasks(listOf(taskId), hours = 3, at = start.plus(Duration.ofDays(8).plusHours(3)))
            assertEquals(tau7, tau(), 1e-12)
            env.tasks.snoozeTasks(listOf(taskId), hours = 3, at = start.plus(Duration.ofDays(8).plusHours(6)))
            assertEquals(tau7 * 1.15, tau(), 1e-12)

            val events = env.tasks.snoozesForTask(taskId)
            assertEquals(3, events.size)
            assertEquals(3, events.first().snoozeHours)
        }

    @Test
    fun completionResetsTheSnoozeStreak() =
        runTest {
            repeat(2) { env.tasks.snoozeTasks(listOf(taskId), at = start.plus(Duration.ofHours(1L + it))) }
            env.tasks.completeTask(taskId, start.plus(Duration.ofHours(5)))
            env.tasks.snoozeTasks(listOf(taskId), at = start.plus(Duration.ofHours(6)))
            // Only one snooze since the completion, so tau is unchanged.
            assertEquals(tau7, tau(), 1e-12)
        }

    @Test
    fun threeEagerCompletionsShortenTauAndUndoRestoresIt() =
        runTest {
            // Daily completions on a weekly task: D = 1 - exp(-1/4.349) = 0.205 each time, below 0.5.
            env.tasks.completeTask(taskId, start.plus(Duration.ofDays(1)))
            env.tasks.completeTask(taskId, start.plus(Duration.ofDays(2)))
            assertEquals(tau7, tau(), 1e-12)
            val third = env.tasks.completeTask(taskId, start.plus(Duration.ofDays(3)))!!
            assertEquals(tau7 * 0.9, tau(), 1e-12)

            val recorded = env.db.completionDao().recentDueness(taskId, 10)
            assertEquals(3, recorded.size)
            assertEquals(0.2053, recorded.first()!!, 1e-4)

            env.tasks.undoCompletion(third)
            assertEquals(tau7, tau(), 1e-9)
        }

    @Test
    fun aLateCompletionBreaksTheEagerStreak() =
        runTest {
            env.tasks.completeTask(taskId, start.plus(Duration.ofDays(1)))
            env.tasks.completeTask(taskId, start.plus(Duration.ofDays(2)))
            // 8 days later: D = 1 - exp(-8/4.349) = 0.84, not eager.
            env.tasks.completeTask(taskId, start.plus(Duration.ofDays(10)))
            assertEquals(tau7, tau(), 1e-12)
        }
}
