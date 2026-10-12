package com.example.homeops.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homeops.data.db.TaskWithPlace
import com.example.homeops.data.repo.TaskRepository
import com.example.homeops.domain.scheduling.Dueness
import com.example.homeops.ui.common.ticker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class TodayItem(
    val taskId: Long,
    val title: String,
    val place: String,
    val dueness: Double,
    val isDue: Boolean,
    val estMinutes: Double,
    val lastCompletedAt: Instant?,
)

data class PendingUndo(
    val eventId: Long,
    val taskTitle: String,
)

data class TodayUiState(
    val isLoading: Boolean = true,
    val due: List<TodayItem> = emptyList(),
    val upcoming: List<TodayItem> = emptyList(),
    /** Sum of prior estimates for due tasks. M3 replaces this with mu_T and T80. */
    val dueMinutesPrior: Double = 0.0,
    val completedToday: Int = 0,
    val now: Instant = Instant.EPOCH,
    val pendingUndo: PendingUndo? = null,
)

/**
 * `@HiltViewModel` lets Compose obtain this via hiltViewModel() with its dependencies injected.
 * The UI observes [state] (a StateFlow: always has a current value) and calls the `on...` functions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val tasks: TaskRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val pendingUndo = MutableStateFlow<PendingUndo?>(null)

        private val now = clock.ticker()

        // Re-queries "completions since local midnight" whenever the date changes.
        private val completedToday =
            now
                .map { it.atZone(ZoneId.systemDefault()).toLocalDate() }
                .distinctUntilChanged()
                .flatMapLatest { date ->
                    tasks.observeCompletionCountSince(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
                }

        val state: StateFlow<TodayUiState> =
            combine(tasks.observeActiveTasks(), now, completedToday, pendingUndo) { rows, t, doneToday, undo ->
                val items = Dueness.orderByDueness(rows.map { it.toItem(t) }, { it.dueness }, { it.title })
                val (due, upcoming) = items.partition { it.isDue }
                TodayUiState(
                    isLoading = false,
                    due = due,
                    upcoming = upcoming,
                    dueMinutesPrior = due.sumOf { it.estMinutes },
                    completedToday = doneToday,
                    now = t,
                    pendingUndo = undo,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

        fun onComplete(item: TodayItem) {
            viewModelScope.launch {
                val eventId = tasks.completeTask(item.taskId) ?: return@launch
                pendingUndo.value = PendingUndo(eventId, item.title)
            }
        }

        fun onUndo(undo: PendingUndo) {
            pendingUndo.value = null
            viewModelScope.launch { tasks.undoCompletion(undo.eventId) }
        }

        fun onUndoDismissed() {
            pendingUndo.value = null
        }

        private fun TaskWithPlace.toItem(now: Instant): TodayItem {
            val d = Dueness.duenessAt(task.lastCompletedAt, task.tauDays, now)
            return TodayItem(
                taskId = task.id,
                title = task.title,
                place = listOfNotNull(roomName, zoneName).joinToString(" · "),
                dueness = d,
                isDue = Dueness.isDue(d),
                estMinutes = task.estMinutesPrior,
                lastCompletedAt = task.lastCompletedAt,
            )
        }
    }
