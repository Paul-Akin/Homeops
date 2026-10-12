package com.example.homeops.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homeops.data.db.RoomEntity
import com.example.homeops.data.repo.HomeRepository
import com.example.homeops.data.repo.TaskRepository
import com.example.homeops.domain.scheduling.Dueness
import com.example.homeops.ui.common.ticker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class RoomSummary(
    val room: RoomEntity,
    val activeTasks: Int,
    val dueTasks: Int,
    /** Highest D among the room's active tasks; drives the heat bar. 0 if none. */
    val maxDueness: Double,
)

data class RoomsUiState(
    val isLoading: Boolean = true,
    val rooms: List<RoomSummary> = emptyList(),
)

@HiltViewModel
class RoomsViewModel
    @Inject
    constructor(
        private val home: HomeRepository,
        tasks: TaskRepository,
        clock: Clock,
    ) : ViewModel() {
        val state: StateFlow<RoomsUiState> =
            combine(home.observeRooms(), tasks.observeActiveTasks(), clock.ticker()) { rooms, active, now ->
                val dByRoom =
                    active.groupBy(
                        { it.task.roomId },
                    ) { Dueness.duenessAt(it.task.lastCompletedAt, it.task.tauDays, now) }
                RoomsUiState(
                    isLoading = false,
                    rooms =
                        rooms.map { room ->
                            val ds = dByRoom[room.id].orEmpty()
                            RoomSummary(room, ds.size, ds.count(Dueness::isDue), ds.maxOrNull() ?: 0.0)
                        },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomsUiState())

        fun onSaveRoom(room: RoomEntity) {
            viewModelScope.launch { home.saveRoom(room) }
        }
    }
