package com.example.homeops.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homeops.ui.common.DuenessBar
import com.example.homeops.ui.common.EmptyState
import com.example.homeops.ui.common.Format
import com.example.homeops.ui.common.SectionHeader
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenRooms: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    // collectAsStateWithLifecycle stops collecting while the app is in the background.
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // LaunchedEffect runs this coroutine whenever pendingUndo changes, e.g. after each completion.
    LaunchedEffect(state.pendingUndo) {
        val undo = state.pendingUndo ?: return@LaunchedEffect
        val result =
            snackbar.showSnackbar(
                message = "Completed: ${undo.taskTitle}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
        if (result == SnackbarResult.ActionPerformed) viewModel.onUndo(undo) else viewModel.onUndoDismissed()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Today") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val empty = state.due.isEmpty() && state.upcoming.isEmpty()
        when {
            state.isLoading -> {
                Unit
            }

            empty -> {
                Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("No tasks yet. Add rooms, then tasks, from the Rooms tab.", Modifier.weight(1f))
                    Button(onClick = onOpenRooms, modifier = Modifier.padding(bottom = 48.dp)) { Text("Go to Rooms") }
                }
            }

            else -> {
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    item {
                        SummaryCard(
                            dueCount = state.due.size,
                            dueMinutes = state.dueMinutesPrior,
                            completedToday = state.completedToday,
                        )
                    }
                    if (state.due.isNotEmpty()) {
                        item { SectionHeader("Due now") }
                        items(state.due, key = { it.taskId }) { TaskRow(it, state.now, viewModel::onComplete) }
                    }
                    if (state.upcoming.isNotEmpty()) {
                        item { SectionHeader("Coming up") }
                        items(state.upcoming, key = { it.taskId }) { TaskRow(it, state.now, viewModel::onComplete) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    dueCount: Int,
    dueMinutes: Double,
    completedToday: Int,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val headline = if (dueCount == 0) "Nothing due. Nice." else "$dueCount due, about ${Format.minutes(dueMinutes)}"
        Text(headline, style = MaterialTheme.typography.headlineSmall)
        Text(
            "Completed today: $completedToday",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskRow(
    item: TodayItem,
    now: Instant,
    onComplete: (TodayItem) -> Unit,
) {
    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = {
            Column {
                Text(
                    "${item.place} · ~${Format.minutes(item.estMinutes)} · " +
                        "last ${Format.ago(item.lastCompletedAt, now)}",
                )
                Row(Modifier.padding(top = 6.dp)) { DuenessBar(item.dueness) }
            }
        },
        trailingContent = {
            FilledTonalIconButton(onClick = { onComplete(item) }) {
                Icon(Icons.Filled.Check, contentDescription = "Mark ${item.title} complete")
            }
        },
    )
    HorizontalDivider()
}
