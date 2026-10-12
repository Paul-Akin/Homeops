package com.example.homeops.data.repo

import androidx.room.withTransaction
import com.example.homeops.data.db.CompletionDao
import com.example.homeops.data.db.CompletionEventEntity
import com.example.homeops.data.db.CompletionLogRow
import com.example.homeops.data.db.HomeOpsDatabase
import com.example.homeops.data.db.TaskDao
import com.example.homeops.data.db.TaskEntity
import com.example.homeops.data.db.TaskWithPlace
import com.example.homeops.domain.estimation.PriorEstimate
import com.example.homeops.domain.model.TaskSource
import com.example.homeops.domain.model.TaskType
import com.example.homeops.domain.scheduling.Dueness
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Validated user input for creating or editing a task. id == 0 means "new". */
data class TaskDraft(
    val id: Long = 0,
    val roomId: Long,
    val zoneId: Long?,
    val title: String,
    val taskType: TaskType,
    val areaFt2: Double?,
    val severity: Int,
    val frequencyDays: Double,
    val isActive: Boolean = true,
)

@Singleton
class TaskRepository
    @Inject
    constructor(
        private val db: HomeOpsDatabase,
        private val taskDao: TaskDao,
        private val completionDao: CompletionDao,
        private val clock: Clock,
    ) {
        fun observeActiveTasks(): Flow<List<TaskWithPlace>> = taskDao.observeActiveWithPlace()

        fun observeTasksForRoom(roomId: Long): Flow<List<TaskWithPlace>> = taskDao.observeForRoomWithPlace(roomId)

        fun observeRecentCompletions(limit: Int = 200): Flow<List<CompletionLogRow>> =
            completionDao.observeRecent(limit)

        fun observeCompletionsForTask(taskId: Long): Flow<List<CompletionLogRow>> = completionDao.observeForTask(taskId)

        fun observeCompletionCountSince(since: Instant): Flow<Int> = completionDao.observeCountSince(since)

        suspend fun getTask(id: Long): TaskEntity? = taskDao.getById(id)

        /**
         * Creates or updates a task from a draft. tau comes from the nominal frequency (tau = F / ln 5).
         * The prior t0 is recomputed from the table for MANUAL tasks; AI-sourced tasks keep the AI's
         * estimate (they appear in M4/M5). History fields (createdAt, lastCompletedAt) are preserved.
         */
        suspend fun saveTask(draft: TaskDraft): Long {
            val tau = Dueness.tauFromFrequencyDays(draft.frequencyDays)
            val prior = PriorEstimate.priorMinutes(draft.taskType, draft.areaFt2, draft.severity)
            val existing = if (draft.id != 0L) taskDao.getById(draft.id) else null
            return if (existing == null) {
                taskDao.insert(
                    TaskEntity(
                        roomId = draft.roomId,
                        zoneId = draft.zoneId,
                        title = draft.title,
                        taskType = draft.taskType,
                        areaFt2 = draft.areaFt2,
                        severity = draft.severity,
                        estMinutesPrior = prior,
                        tauDays = tau,
                        createdAt = clock.instant(),
                        source = TaskSource.MANUAL,
                        isActive = draft.isActive,
                    ),
                )
            } else {
                taskDao.update(
                    existing.copy(
                        zoneId = draft.zoneId,
                        title = draft.title,
                        taskType = draft.taskType,
                        areaFt2 = draft.areaFt2,
                        severity = draft.severity,
                        estMinutesPrior = if (existing.source == TaskSource.MANUAL) prior else existing.estMinutesPrior,
                        tauDays = tau,
                        isActive = draft.isActive,
                    ),
                )
                existing.id
            }
        }

        /** Hard delete. Also deletes the task's completion history (FK cascade); the UI warns first. */
        suspend fun deleteTask(id: Long) = taskDao.deleteById(id)

        /**
         * Logs a manual completion and advances the task's clock, atomically.
         * `withTransaction` runs the block as one SQLite transaction: either both writes land or neither.
         *
         * @return the new completion event id (for Undo), or null if the task no longer exists.
         */
        suspend fun completeTask(
            taskId: Long,
            at: Instant = clock.instant(),
        ): Long? =
            db.withTransaction {
                val task = taskDao.getById(taskId) ?: return@withTransaction null
                val onTime = Dueness.wasOnTime(task.lastCompletedAt, task.createdAt, task.tauDays, at)
                val eventId =
                    completionDao.insert(
                        CompletionEventEntity(
                            taskId = taskId,
                            completedAt = at,
                            estMinutesAtStart = task.estMinutesPrior,
                            wasOnTime = onTime,
                        ),
                    )
                // Guard against out-of-order completions (e.g. a back-dated entry): keep the latest time.
                val latest = task.lastCompletedAt
                if (latest == null || at.isAfter(latest)) taskDao.setLastCompletedAt(taskId, at)
                eventId
            }

        /**
         * Removes a completion event (Undo) and restores the task's lastCompletedAt to its latest
         * remaining completion, or null if none remain.
         */
        suspend fun undoCompletion(eventId: Long) =
            db.withTransaction {
                val event = completionDao.getById(eventId) ?: return@withTransaction
                completionDao.deleteById(eventId)
                taskDao.setLastCompletedAt(event.taskId, completionDao.latestCompletedAt(event.taskId))
            }
    }
