package com.example.homeops.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Version history (each bump needs a Migration in this package and an exported schema JSON
 * under app/schemas/, per DESIGN.md Section 13):
 *  1 - M1: rooms, zones, tasks, completion_events.
 *  2 - M2: tasks.weight, completion_events.duenessAtCompletion, snooze_events (MIGRATION_1_2).
 */
@Database(
    entities = [
        RoomEntity::class,
        ZoneEntity::class,
        TaskEntity::class,
        CompletionEventEntity::class,
        SnoozeEventEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HomeOpsDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao

    abstract fun zoneDao(): ZoneDao

    abstract fun taskDao(): TaskDao

    abstract fun completionDao(): CompletionDao

    abstract fun snoozeDao(): SnoozeDao

    companion object {
        const val NAME = "homeops.db"
    }
}
