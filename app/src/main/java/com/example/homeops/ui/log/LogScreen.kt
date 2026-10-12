package com.example.homeops.ui.log

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homeops.ui.common.EmptyState
import com.example.homeops.ui.common.Format
import com.example.homeops.ui.theme.StatusColors

/** Completion log: the most recent 200 completions across all tasks, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(viewModel: LogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Completion log") }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when {
            state.isLoading -> {
                Unit
            }

            state.rows.isEmpty() -> {
                EmptyState("Nothing completed yet. Tap ✓ on the Today tab.", Modifier.padding(padding))
            }

            else -> {
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    items(state.rows, key = { it.id }) { row ->
                        ListItem(
                            headlineContent = { Text(row.taskTitle) },
                            supportingContent = { Text("${row.roomName} · ${Format.dateTime(row.completedAt)}") },
                            trailingContent = {
                                if (row.wasOnTime) {
                                    Text("On time", color = StatusColors.OnTrack)
                                } else {
                                    Text("Late", color = StatusColors.Overdue)
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
