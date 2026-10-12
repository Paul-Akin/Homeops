package com.example.homeops.ui.rooms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homeops.domain.model.displayName
import com.example.homeops.ui.common.DuenessBar
import com.example.homeops.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(
    onOpenRoom: (Long) -> Unit,
    viewModel: RoomsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Rooms") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add room")
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when {
            state.isLoading -> {
                Unit
            }

            state.rooms.isEmpty() -> {
                EmptyState("No rooms yet. Tap + to add your first room.", Modifier.padding(padding))
            }

            else -> {
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    items(state.rooms, key = { it.room.id }) { summary ->
                        ListItem(
                            headlineContent = { Text(summary.room.name) },
                            supportingContent = {
                                Column {
                                    Text(
                                        "${summary.room.roomType.displayName()} · floor ${summary.room.floor} · " +
                                            "${summary.activeTasks} tasks, ${summary.dueTasks} due",
                                    )
                                    if (summary.activeTasks > 0) {
                                        DuenessBar(summary.maxDueness, Modifier.padding(top = 6.dp))
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onOpenRoom(summary.room.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (adding) {
        RoomEditDialog(
            initial = RoomForm(),
            onSave = {
                viewModel.onSaveRoom(it)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}
