package com.example.homeops.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Builds a real version-1 database file with the M1 schema and existing data, then opens it with the
 * v2 Room database and MIGRATION_1_2. Room validates every table against the v2 entities on open,
 * so this fails if the migration leaves any column, default, foreign key, or index different.
 *
 * The v1 DDL below mirrors what Room generated for the M1 entities (app/schemas/.../1.json).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class Migration1To2Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private val created = Instant.parse("2026-01-05T09:00:00Z")
    private val done = Instant.parse("2026-01-07T18:30:00Z")

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createVersion1() {
        val db =
            SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(dbName).apply { parentFile?.mkdirs() },
                null,
            )
        V1_SCHEMA.forEach(db::execSQL)
        db.execSQL(
            "INSERT INTO rooms (id, name, roomType, floor, areaFt2, weight, sortOrder) " +
                "VALUES (1, 'Kitchen', 'KITCHEN', 1, 180.0, 1.5, 0)",
        )
        db.execSQL(
            "INSERT INTO tasks (id, roomId, zoneId, title, taskType, areaFt2, severity, estMinutesPrior, " +
                "sigmaLn, tauDays, lastCompletedAt, createdAt, source, isActive, supplies) VALUES " +
                "(1, 1, NULL, 'Wipe counters', 'WIPE', NULL, 3, 4.75, 0.4, 4.349, ${done.toEpochMilli()}, " +
                "${created.toEpochMilli()}, 'MANUAL', 1, '[]')",
        )
        db.execSQL(
            "INSERT INTO completion_events (id, taskId, completedAt, actualMinutes, estMinutesAtStart, " +
                "beforePhotoId, afterPhotoId, wasOnTime) VALUES (1, 1, ${done.toEpochMilli()}, NULL, 4.75, NULL, NULL, 1)",
        )
        db.version = 1
        db.close()
    }

    @Test
    fun migratesV1DataToV2() =
        runTest {
            createVersion1()

            val db =
                Room
                    .databaseBuilder(context, HomeOpsDatabase::class.java, dbName)
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
            try {
                // First query triggers open, migration, and Room's schema validation.
                val task = db.taskDao().getById(1)!!
                assertEquals("Wipe counters", task.title)
                assertEquals(1.0, task.weight, 0.0)
                assertEquals(done, task.lastCompletedAt)

                val rows = db.taskDao().observeActiveWithPlace().first()
                assertEquals(1.5, rows.single().roomWeight, 0.0)

                val event = db.completionDao().getById(1)!!
                assertNull(event.duenessAtCompletion)
                assertEquals(listOf<Double?>(null), db.completionDao().recentDueness(1, 10))

                // The new table works, including its foreign key to tasks.
                db.snoozeDao().insert(SnoozeEventEntity(taskId = 1, at = done, snoozeHours = 3))
                assertEquals(1, db.snoozeDao().countSince(1, created))
            } finally {
                db.close()
            }
        }

    private companion object {
        val V1_SCHEMA =
            listOf(
                "CREATE TABLE IF NOT EXISTS `rooms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `roomType` TEXT NOT NULL, `floor` INTEGER NOT NULL, `areaFt2` REAL, " +
                    "`weight` REAL NOT NULL, `sortOrder` INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS `zones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`roomId` INTEGER NOT NULL, `name` TEXT NOT NULL, `areaFt2` REAL, `mapX` REAL, `mapY` REAL, " +
                    "FOREIGN KEY(`roomId`) REFERENCES `rooms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_zones_roomId` ON `zones` (`roomId`)",
                "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`roomId` INTEGER NOT NULL, `zoneId` INTEGER, `title` TEXT NOT NULL, `taskType` TEXT NOT NULL, " +
                    "`areaFt2` REAL, `severity` INTEGER NOT NULL, `estMinutesPrior` REAL NOT NULL, " +
                    "`sigmaLn` REAL NOT NULL, `tauDays` REAL NOT NULL, `lastCompletedAt` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, `source` TEXT NOT NULL, `isActive` INTEGER NOT NULL, " +
                    "`supplies` TEXT NOT NULL, " +
                    "FOREIGN KEY(`roomId`) REFERENCES `rooms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`zoneId`) REFERENCES `zones`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                "CREATE INDEX IF NOT EXISTS `index_tasks_roomId` ON `tasks` (`roomId`)",
                "CREATE INDEX IF NOT EXISTS `index_tasks_zoneId` ON `tasks` (`zoneId`)",
                "CREATE TABLE IF NOT EXISTS `completion_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`taskId` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, `actualMinutes` REAL, " +
                    "`estMinutesAtStart` REAL, `beforePhotoId` INTEGER, `afterPhotoId` INTEGER, " +
                    "`wasOnTime` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_completion_events_taskId` ON `completion_events` (`taskId`)",
                "CREATE INDEX IF NOT EXISTS `index_completion_events_completedAt` ON `completion_events` (`completedAt`)",
            )
    }
}
