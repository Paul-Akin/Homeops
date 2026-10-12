package com.example.homeops.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homeops.data.db.CompletionLogRow
import com.example.homeops.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LogUiState(
    val isLoading: Boolean = true,
    val rows: List<CompletionLogRow> = emptyList(),
)

@HiltViewModel
class LogViewModel
    @Inject
    constructor(
        tasks: TaskRepository,
    ) : ViewModel() {
        val state: StateFlow<LogUiState> =
            tasks
                .observeRecentCompletions(limit = 200)
                .map { LogUiState(isLoading = false, rows = it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState())
    }
