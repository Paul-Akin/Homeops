package com.example.homeops.ui.rooms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.homeops.data.db.RoomEntity
import com.example.homeops.data.db.ZoneEntity
import com.example.homeops.data.repo.HomeRepository
import com.example.homeops.data.repo.TaskRepository
import com.example.homeops.domain.scheduling.Dueness
import com.example.homeops.ui.common.ticker
import com.example.homeops.ui.nav.RoomDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class RoomTaskRow(
    val taskId: Long,
    val title: String,
    val zoneName: String?,
    val frequencyDays: Double,
    val estMinutes: Double,
    val dueness: Double,
    val isActive: Boolean,
)

data class RoomDetailUiState(
    val isLoading: Boolean = true,
    /** Null after loading means the room was deleted (or never existed); the screen navigates back. */
    val room: RoomEntity? = null,
    val zones: List<ZoneEntity> = emptyList(),
    val tasks: List<RoomTaskRow> = emptyList(),
)

@HiltViewModel
class RoomDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val home: HomeRepository,
        tasks: TaskRepository,
        clock: Clock,
    ) : ViewModel() {
        /** The navigation argument, decoded from the type-safe route. */
        val roomId: Long = savedStateHandle.toRoute<RoomDetailRoute>().roomId

        private val deleting = MutableStateFlow(false)

        val state: StateFlow<RoomDetailUiState> =
            combine(
                home.observeRoom(roomId),
                home.observeZones(roomId),
                tasks.observeTasksForRoom(roomId),
                clock.ticker(),
                deleting,
            ) { room, zones, rows, now, isDeleting ->
                RoomDetailUiState(
                    isLoading = false,
                    room = room.takeUnless { isDeleting },
                    zones = zones,
                    tasks =
                        rows.map {
                            RoomTaskRow(
                                taskId = it.task.id,
                                title = it.task.title,
                                zoneName = it.zoneName,
                                frequencyDays = Dueness.frequencyDaysFromTau(it.task.tauDays),
                                estMinutes = it.task.estMinutesPrior,
                                dueness = Dueness.duenessAt(it.task.lastCompletedAt, it.task.tauDays, now),
                                isActive = it.task.isActive,
                            )
                        },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomDetailUiState())

        fun onSaveRoom(room: RoomEntity) {
            viewModelScope.launch { home.saveRoom(room) }
        }

        fun onDeleteRoom() {
            val room = state.value.room ?: return
            deleting.value = true
            viewModelScope.launch { home.deleteRoom(room) }
        }

        fun onSaveZone(zone: ZoneEntity) {
            viewModelScope.launch { home.saveZone(zone) }
        }

        fun onDeleteZone(zone: ZoneEntity) {
            viewModelScope.launch { home.deleteZone(zone) }
        }
    }
