package com.example.homeops.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Schema migrations (DESIGN.md Section 13). Each one must leave the database exactly matching the
// entities for the new version; Room verifies this on open and refuses a mismatched schema.
// Migration1To2Test builds a real v1 file and upgrades it to prove that.

/** v1 -> v2 (M2): task weight, D at completion, and the snooze log. */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `weight` REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE `completion_events` ADD COLUMN `duenessAtCompletion` REAL")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `snooze_events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`taskId` INTEGER NOT NULL, " +
                    "`at` INTEGER NOT NULL, " +
                    "`snoozeHours` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_snooze_events_taskId` ON `snooze_events` (`taskId`)")
        }
    }

/** Every migration, in order. Passed to Room.databaseBuilder(...).addMigrations(*ALL_MIGRATIONS). */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
