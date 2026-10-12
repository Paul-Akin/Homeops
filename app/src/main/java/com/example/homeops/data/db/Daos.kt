package com.example.homeops.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

// DAOs are interfaces; Room's annotation processor (KSP) generates the SQL-backed
// implementations at build time. Functions returning Flow re-emit whenever a table they read
// changes, which is what keeps the UI live. `suspend` functions run off the main thread.
// Per DESIGN.md Section 13, DAOs hold queries only, no business logic.

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :id")
    fun observeById(id: Long): Flow<RoomEntity?>

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun getById(id: Long): RoomEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM rooms")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(room: RoomEntity): Long

    @Update
    suspend fun update(room: RoomEntity)

    @Delete
    suspend fun delete(room: RoomEntity)
}

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones WHERE roomId = :roomId ORDER BY name COLLATE NOCASE")
    fun observeForRoom(roomId: Long): Flow<List<ZoneEntity>>

    @Insert
    suspend fun insert(zone: ZoneEntity): Long

    @Update
    suspend fun update(zone: ZoneEntity)

    @Delete
    suspend fun delete(zone: ZoneEntity)
}

/** A task joined with the names needed to display it. `@Embedded` flattens TaskEntity's columns in. */
data class TaskWithPlace(
    @Embedded val task: TaskEntity,
    val roomName: String,
    val roomWeight: Double,
    val zoneName: String?,
)

@Dao
interface TaskDao {
    @Query(
        """
        SELECT tasks.*, rooms.name AS roomName, rooms.weight AS roomWeight, zones.name AS zoneName
        FROM tasks
        JOIN rooms ON rooms.id = tasks.roomId
        LEFT JOIN zones ON zones.id = tasks.zoneId
        WHERE tasks.isActive = 1
        """,
    )
    fun observeActiveWithPlace(): Flow<List<TaskWithPlace>>

    /** One-shot read of the same rows, for background work (reminders) that does not observe. */
    @Query(
        """
        SELECT tasks.*, rooms.name AS roomName, rooms.weight AS roomWeight, zones.name AS zoneName
        FROM tasks
        JOIN rooms ON rooms.id = tasks.roomId
        LEFT JOIN zones ON zones.id = tasks.zoneId
        WHERE tasks.isActive = 1
        """,
    )
    suspend fun getActiveWithPlace(): List<TaskWithPlace>

    @Query(
        """
        SELECT tasks.*, rooms.name AS roomName, rooms.weight AS roomWeight, zones.name AS zoneName
        FROM tasks
        JOIN rooms ON rooms.id = tasks.roomId
        LEFT JOIN zones ON zones.id = tasks.zoneId
        WHERE tasks.roomId = :roomId
        ORDER BY tasks.isActive DESC, tasks.title COLLATE NOCASE
        """,
    )
    fun observeForRoomWithPlace(roomId: Long): Flow<List<TaskWithPlace>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE tasks SET lastCompletedAt = :at WHERE id = :taskId")
    suspend fun setLastCompletedAt(
        taskId: Long,
        at: Instant?,
    )

    @Query("UPDATE tasks SET tauDays = :tauDays WHERE id = :taskId")
    suspend fun setTau(
        taskId: Long,
        tauDays: Double,
    )
}

/** One row of the completion log screen. */
data class CompletionLogRow(
    val id: Long,
    val taskId: Long,
    val completedAt: Instant,
    val actualMinutes: Double?,
    val wasOnTime: Boolean,
    val taskTitle: String,
    val roomName: String,
)

@Dao
interface CompletionDao {
    @Insert
    suspend fun insert(event: CompletionEventEntity): Long

    @Query("SELECT * FROM completion_events WHERE id = :id")
    suspend fun getById(id: Long): CompletionEventEntity?

    @Query("DELETE FROM completion_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT MAX(completedAt) FROM completion_events WHERE taskId = :taskId")
    suspend fun latestCompletedAt(taskId: Long): Instant?

    @Query(
        """
        SELECT c.id, c.taskId, c.completedAt, c.actualMinutes, c.wasOnTime,
               t.title AS taskTitle, r.name AS roomName
        FROM completion_events c
        JOIN tasks t ON t.id = c.taskId
        JOIN rooms r ON r.id = t.roomId
        ORDER BY c.completedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<CompletionLogRow>>

    @Query(
        """
        SELECT c.id, c.taskId, c.completedAt, c.actualMinutes, c.wasOnTime,
               t.title AS taskTitle, r.name AS roomName
        FROM completion_events c
        JOIN tasks t ON t.id = c.taskId
        JOIN rooms r ON r.id = t.roomId
        WHERE c.taskId = :taskId
        ORDER BY c.completedAt DESC
        """,
    )
    fun observeForTask(taskId: Long): Flow<List<CompletionLogRow>>

    @Query("SELECT COUNT(*) FROM completion_events WHERE completedAt >= :since")
    fun observeCountSince(since: Instant): Flow<Int>

    /** D at completion for a task, newest first (input to the "eager completion" tau rule). */
    @Query(
        "SELECT duenessAtCompletion FROM completion_events WHERE taskId = :taskId ORDER BY completedAt DESC LIMIT :limit",
    )
    suspend fun recentDueness(
        taskId: Long,
        limit: Int,
    ): List<Double?>

    /** Completion times (epoch ms) since a cutoff, for the reminder-time learner. */
    @Query("SELECT completedAt FROM completion_events WHERE completedAt >= :sinceEpochMillis")
    suspend fun completionMillisSince(sinceEpochMillis: Long): List<Long>
}

@Dao
interface SnoozeDao {
    @Insert
    suspend fun insert(event: SnoozeEventEntity): Long

    /** Snoozes of a task strictly after [since], i.e. the current streak when since = last completion. */
    @Query("SELECT COUNT(*) FROM snooze_events WHERE taskId = :taskId AND at > :since")
    suspend fun countSince(
        taskId: Long,
        since: Instant,
    ): Int

    @Query("SELECT * FROM snooze_events WHERE taskId = :taskId ORDER BY at DESC")
    suspend fun forTask(taskId: Long): List<SnoozeEventEntity>
}
