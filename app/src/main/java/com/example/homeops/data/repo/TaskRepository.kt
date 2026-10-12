package com.example.homeops.data.repo

import androidx.room.withTransaction
import com.example.homeops.data.db.CompletionDao
import com.example.homeops.data.db.CompletionEventEntity
import com.example.homeops.data.db.CompletionLogRow
import com.example.homeops.data.db.HomeOpsDatabase
import com.example.homeops.data.db.SnoozeDao
import com.example.homeops.data.db.SnoozeEventEntity
import com.example.homeops.data.db.TaskDao
import com.example.homeops.data.db.TaskEntity
import com.example.homeops.data.db.TaskWithPlace
import com.example.homeops.domain.Tuning
import com.example.homeops.domain.estimation.PriorEstimate
import com.example.homeops.domain.model.TaskSource
import com.example.homeops.domain.model.TaskType
import com.example.homeops.domain.scheduling.DecayScheduler
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
    /** w_task in P = D * w_room * w_task. */
    val weight: Double = 1.0,
)

@Singleton
class TaskRepository
    @Inject
    constructor(
        private val db: HomeOpsDatabase,
        private val taskDao: TaskDao,
        private val completionDao: CompletionDao,
        private val snoozeDao: SnoozeDao,
        private val clock: Clock,
    ) {
        fun observeActiveTasks(): Flow<List<TaskWithPlace>> = taskDao.observeActiveWithPlace()

        fun observeTasksForRoom(roomId: Long): Flow<List<TaskWithPlace>> = taskDao.observeForRoomWithPlace(roomId)

        fun observeRecentCompletions(limit: Int = 200): Flow<List<CompletionLogRow>> =
            completionDao.observeRecent(limit)

        fun observeCompletionsForTask(taskId: Long): Flow<List<CompletionLogRow>> = completionDao.observeForTask(taskId)

        fun observeCompletionCountSince(since: Instant): Flow<Int> = completionDao.observeCountSince(since)

        suspend fun getTask(id: Long): TaskEntity? = taskDao.getById(id)

        /** One-shot snapshot of active tasks, for reminders (background work does not observe Flows). */
        suspend fun activeTasksNow(): List<TaskWithPlace> = taskDao.getActiveWithPlace()

        /** Completion instants since [since], for NotificationTimeLearner. */
        suspend fun completionTimesSince(since: Instant): List<Instant> =
            completionDao.completionMillisSince(since.toEpochMilli()).map(Instant::ofEpochMilli)

        suspend fun snoozesForTask(taskId: Long): List<SnoozeEventEntity> = snoozeDao.forTask(taskId)

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
                        weight = draft.weight,
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
                        weight = draft.weight,
                    ),
                )
                existing.id
            }
        }

        /** Hard delete. Also deletes the task's completion history (FK cascade); the UI warns first. */
        suspend fun deleteTask(id: Long) = taskDao.deleteById(id)

        /**
         * Logs a manual completion and advances the task's clock, atomically.
         * `withTransaction` runs the block as one SQLite transaction: either all writes land or none.
         *
         * Also records D at completion and applies the "completed while D < 0.5, three times in a row"
         * rule, tau <- 0.9 tau (DESIGN.md Section 5.2). D is measured from the previous completion, or
         * from task creation for the first one, consistent with the on-time flag.
         *
         * @return the new completion event id (for Undo), or null if the task no longer exists.
         */
        suspend fun completeTask(
            taskId: Long,
            at: Instant = clock.instant(),
        ): Long? =
            db.withTransaction {
                val task = taskDao.getById(taskId) ?: return@withTransaction null
                val reference = task.lastCompletedAt ?: task.createdAt
                val d = Dueness.dueness(Dueness.daysBetween(reference, at), task.tauDays)
                val eventId =
                    completionDao.insert(
                        CompletionEventEntity(
                            taskId = taskId,
                            completedAt = at,
                            estMinutesAtStart = task.estMinutesPrior,
                            wasOnTime = d <= Tuning.ON_TIME_THRESHOLD,
                            duenessAtCompletion = d,
                        ),
                    )
                // Guard against out-of-order completions (e.g. a back-dated entry): keep the latest time.
                val latest = task.lastCompletedAt
                if (latest == null || at.isAfter(latest)) taskDao.setLastCompletedAt(taskId, at)

                val recent = completionDao.recentDueness(taskId, ADAPTATION_HISTORY)
                val newTau = DecayScheduler.tauAfterCompletion(task.tauDays, recent)
                if (newTau != task.tauDays) taskDao.setTau(taskId, newTau)
                eventId
            }

        /**
         * "Snooze 3 h" from a reminder: logs one SnoozeEvent per task and applies the "snoozed three
         * times in a row" rule, tau <- 1.15 tau. The streak counts snoozes since the last completion
         * (or creation). Tasks that no longer exist are skipped.
         */
        suspend fun snoozeTasks(
            taskIds: List<Long>,
            hours: Int = Tuning.SNOOZE_HOURS,
            at: Instant = clock.instant(),
        ) = db.withTransaction {
            for (taskId in taskIds.distinct()) {
                val task = taskDao.getById(taskId) ?: continue
                snoozeDao.insert(SnoozeEventEntity(taskId = taskId, at = at, snoozeHours = hours))
                val streak = snoozeDao.countSince(taskId, task.lastCompletedAt ?: task.createdAt)
                val newTau = DecayScheduler.tauAfterSnooze(task.tauDays, streak)
                if (newTau != task.tauDays) taskDao.setTau(taskId, newTau)
            }
        }

        /**
         * Removes a completion event (Undo) and restores the task's lastCompletedAt to its latest
         * remaining completion, or null if none remain. If the undone completion was the newest one
         * and it triggered the tau <- 0.9 tau rule, that adjustment is reversed too.
         */
        suspend fun undoCompletion(eventId: Long) =
            db.withTransaction {
                val event = completionDao.getById(eventId) ?: return@withTransaction
                val task = taskDao.getById(event.taskId)
                val wasNewest = completionDao.latestCompletedAt(event.taskId) == event.completedAt
                val recent = completionDao.recentDueness(event.taskId, ADAPTATION_HISTORY)
                completionDao.deleteById(eventId)
                taskDao.setLastCompletedAt(event.taskId, completionDao.latestCompletedAt(event.taskId))
                if (task != null && wasNewest && DecayScheduler.tauAfterCompletion(1.0, recent) != 1.0) {
                    taskDao.setTau(task.id, DecayScheduler.clampTau(task.tauDays / Tuning.TAU_FACTOR_EAGER))
                }
            }

        private companion object {
            /** How many recent completions to read for the streak rule; longer streaks are unrealistic. */
            const val ADAPTATION_HISTORY = 100
        }
    }
