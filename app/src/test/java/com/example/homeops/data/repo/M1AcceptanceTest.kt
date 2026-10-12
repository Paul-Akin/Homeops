package com.example.homeops.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.homeops.data.db.HomeOpsDatabase
import com.example.homeops.data.db.RoomEntity
import com.example.homeops.data.db.ZoneEntity
import com.example.homeops.domain.model.RoomType
import com.example.homeops.domain.model.TaskType
import com.example.homeops.domain.scheduling.Dueness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * DESIGN.md M1 acceptance: create 3 rooms and 10 tasks, complete them, data survives restart.
 * "Restart" is simulated by closing the file-backed database and opening it again.
 *
 * Robolectric supplies an Android Context on the JVM; it uses JUnit 4 (run via the vintage engine).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class M1AcceptanceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "m1-acceptance.db"
    private val created = Instant.parse("2026-01-05T09:00:00Z")
    private lateinit var db: HomeOpsDatabase

    private fun open(): HomeOpsDatabase = Room.databaseBuilder(context, HomeOpsDatabase::class.java, dbName).build()

    private fun repos(
        db: HomeOpsDatabase,
        now: Instant,
    ): Pair<HomeRepository, TaskRepository> {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        return HomeRepository(db.roomDao(), db.zoneDao()) to TaskRepository(db, db.taskDao(), db.completionDao(), clock)
    }

    private fun weekly(
        roomId: Long,
        title: String,
        type: TaskType,
    ) = TaskDraft(
        roomId = roomId,
        zoneId = null,
        title = title,
        taskType = type,
        areaFt2 = null,
        severity = 3,
        frequencyDays = 7.0,
    )

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        db = open()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun threeRoomsTenTasksCompletedAndPersisted() =
        runTest {
            val (home, tasksAtCreate) = repos(db, created)

            val kitchen = home.saveRoom(RoomEntity(name = "Kitchen", roomType = RoomType.KITCHEN, areaFt2 = 180.0))
            val bath = home.saveRoom(RoomEntity(name = "Bathroom", roomType = RoomType.BATHROOM))
            val living = home.saveRoom(RoomEntity(name = "Living room", roomType = RoomType.LIVING, weight = 1.5))
            val stove = home.saveZone(ZoneEntity(roomId = kitchen, name = "Stovetop", areaFt2 = 6.0))

            val specs =
                listOf(
                    Triple(kitchen, "Wipe counters", TaskType.WIPE),
                    Triple(kitchen, "Degrease stovetop", TaskType.SCRUB),
                    Triple(kitchen, "Mop floor", TaskType.MOP),
                    Triple(kitchen, "Take out trash", TaskType.TRASH),
                    Triple(bath, "Scrub toilet", TaskType.SCRUB),
                    Triple(bath, "Wipe mirror", TaskType.WIPE),
                    Triple(bath, "Mop floor", TaskType.MOP),
                    Triple(living, "Vacuum rug", TaskType.VACUUM),
                    Triple(living, "Dust shelves", TaskType.DUST),
                    Triple(living, "Declutter coffee table", TaskType.DECLUTTER),
                )
            val taskIds =
                specs.map { (roomId, title, type) ->
                    tasksAtCreate.saveTask(
                        TaskDraft(
                            roomId = roomId,
                            zoneId = if (title == "Degrease stovetop") stove else null,
                            title = title,
                            taskType = type,
                            areaFt2 = null,
                            severity = 3,
                            frequencyDays = 7.0,
                        ),
                    )
                }

            // Derived fields come from the domain formulas.
            val vacuum = tasksAtCreate.getTask(taskIds[7])!!
            assertEquals(9.0, vacuum.estMinutesPrior, 1e-9) // 3 + 0.04 * 150 * 1.0
            assertEquals(Dueness.tauFromFrequencyDays(7.0), vacuum.tauDays, 1e-12)
            assertEquals(10, tasksAtCreate.observeActiveTasks().first().size)

            // Complete everything two days later: D = 1 - exp(-2/4.349) = 0.369 <= 0.9, so on time.
            val completedAt = created.plus(Duration.ofDays(2))
            val (_, tasksLater) = repos(db, completedAt)
            taskIds.forEach { assertNotNull(tasksLater.completeTask(it)) }

            val log = tasksLater.observeRecentCompletions().first()
            assertEquals(10, log.size)
            assertTrue(log.all { it.wasOnTime && it.completedAt == completedAt })

            // Restart: close and reopen the same database file.
            db.close()
            db = open()
            val (homeAfter, tasksAfter) = repos(db, completedAt)
            assertEquals(listOf("Kitchen", "Bathroom", "Living room"), homeAfter.observeRooms().first().map { it.name })
            assertEquals(listOf("Stovetop"), homeAfter.observeZones(kitchen).first().map { it.name })
            val active = tasksAfter.observeActiveTasks().first()
            assertEquals(10, active.size)
            assertTrue(active.all { it.task.lastCompletedAt == completedAt })
            assertEquals(10, tasksAfter.observeRecentCompletions().first().size)
            assertEquals("Stovetop", active.single { it.task.title == "Degrease stovetop" }.zoneName)
        }

    @Test
    fun undoRestoresPreviousCompletionTime() =
        runTest {
            val (home, tasks) = repos(db, created)
            val room = home.saveRoom(RoomEntity(name = "Kitchen", roomType = RoomType.KITCHEN))
            val id = tasks.saveTask(weekly(room, "Wipe", TaskType.WIPE))

            val first = created.plus(Duration.ofDays(1))
            val second = created.plus(Duration.ofDays(3))
            tasks.completeTask(id, first)
            val secondEvent = tasks.completeTask(id, second)!!
            assertEquals(second, tasks.getTask(id)!!.lastCompletedAt)

            tasks.undoCompletion(secondEvent)
            assertEquals(first, tasks.getTask(id)!!.lastCompletedAt)
            assertEquals(1, tasks.observeCompletionsForTask(id).first().size)
        }

    @Test
    fun lateCompletionIsFlagged() =
        runTest {
            val (home, tasks) = repos(db, created)
            val room = home.saveRoom(RoomEntity(name = "Garage", roomType = RoomType.GARAGE))
            val id = tasks.saveTask(weekly(room, "Sweep", TaskType.OTHER))
            // 12 days on a weekly task: D = 1 - exp(-12/4.349) = 0.937 > 0.9, so late.
            tasks.completeTask(id, created.plus(Duration.ofDays(12)))
            assertFalse(
                tasks
                    .observeCompletionsForTask(id)
                    .first()
                    .single()
                    .wasOnTime,
            )
        }

    @Test
    fun deletingZoneKeepsTaskAndDeletingRoomCascades() =
        runTest {
            val (home, tasks) = repos(db, created)
            val roomId = home.saveRoom(RoomEntity(name = "Bath", roomType = RoomType.BATHROOM))
            val zoneId = home.saveZone(ZoneEntity(roomId = roomId, name = "Sink"))
            val taskId = tasks.saveTask(weekly(roomId, "Scrub sink", TaskType.SCRUB).copy(zoneId = zoneId))
            tasks.completeTask(taskId, created.plus(Duration.ofDays(1)))

            home.deleteZone(home.observeZones(roomId).first().single())
            assertNull(tasks.getTask(taskId)!!.zoneId)

            home.deleteRoom(home.observeRooms().first().single())
            assertNull(tasks.getTask(taskId))
            assertTrue(tasks.observeRecentCompletions().first().isEmpty())
        }
}
