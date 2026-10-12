package com.example.homeops.ui.rooms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homeops.data.db.ZoneEntity
import com.example.homeops.domain.model.displayName
import com.example.homeops.ui.common.ConfirmDialog
import com.example.homeops.ui.common.DuenessBar
import com.example.homeops.ui.common.Format
import com.example.homeops.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    onBack: () -> Unit,
    onEditTask: (roomId: Long, taskId: Long) -> Unit,
    viewModel: RoomDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingRoom by remember { mutableStateOf(false) }
    var confirmDeleteRoom by remember { mutableStateOf(false) }
    // Zone dialog: null = closed; a ZoneEntity with id 0 = adding.
    var editingZone by remember { mutableStateOf<ZoneEntity?>(null) }
    var deletingZone by remember { mutableStateOf<ZoneEntity?>(null) }

    // Leave the screen once the room is gone (deleted here, or missing).
    LaunchedEffect(state.isLoading, state.room) {
        if (!state.isLoading && state.room == null) onBack()
    }
    val room = state.room ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(room.name) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(
                        onClick = { editingRoom = true },
                    ) { Icon(Icons.Filled.Edit, contentDescription = "Edit room") }
                    IconButton(onClick = { confirmDeleteRoom = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete room")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEditTask(room.id, 0L) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add task") },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                val details =
                    listOfNotNull(
                        room.roomType.displayName(),
                        "floor ${room.floor}",
                        room.areaFt2?.let { Format.ft2(it) },
                        "weight ${Format.days(room.weight)}",
                    ).joinToString(" · ")
                Text(
                    details,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                Row(Modifier.padding(end = 8.dp)) {
                    SectionHeader("Zones", Modifier.weight(1f))
                    TextButton(onClick = { editingZone = ZoneEntity(roomId = room.id, name = "") }) { Text("Add zone") }
                }
            }
            if (state.zones.isEmpty()) {
                item {
                    Text(
                        "No zones. Zones are optional sub-areas like \"Under sink\".",
                        Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.zones, key = { "zone-${it.id}" }) { zone ->
                ListItem(
                    headlineContent = { Text(zone.name) },
                    supportingContent =
                        if (zone.areaFt2 != null) {
                            { Text(Format.ft2(zone.areaFt2)) }
                        } else {
                            null
                        },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editingZone = zone }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit ${zone.name}")
                            }
                            IconButton(onClick = { deletingZone = zone }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${zone.name}")
                            }
                        }
                    },
                )
            }

            item { SectionHeader("Tasks") }
            if (state.tasks.isEmpty()) {
                item { Text("No tasks yet. Tap \"Add task\".", Modifier.padding(horizontal = 16.dp)) }
            }
            items(state.tasks, key = { "task-${it.taskId}" }) { task ->
                ListItem(
                    headlineContent = { Text(if (task.isActive) task.title else "${task.title} (paused)") },
                    supportingContent = {
                        Column {
                            Text(
                                listOfNotNull(
                                    task.zoneName ?: "Whole room",
                                    "every ${Format.days(task.frequencyDays)} d",
                                    "~${Format.minutes(task.estMinutes)}",
                                ).joinToString(" · "),
                            )
                            if (task.isActive) DuenessBar(task.dueness, Modifier.padding(top = 6.dp))
                        }
                    },
                    modifier =
                        Modifier
                            .clickable { onEditTask(room.id, task.taskId) }
                            .alpha(if (task.isActive) 1f else 0.6f),
                )
                HorizontalDivider()
            }
            // Spacer row so the FAB never covers the last task.
            item { Text("", Modifier.padding(bottom = 72.dp)) }
        }
    }

    if (editingRoom) {
        RoomEditDialog(
            initial = RoomForm.from(room),
            onSave = {
                viewModel.onSaveRoom(it)
                editingRoom = false
            },
            onDismiss = { editingRoom = false },
        )
    }
    if (confirmDeleteRoom) {
        ConfirmDialog(
            title = "Delete ${room.name}?",
            message = "This permanently deletes the room, its zones, its tasks, and their completion history.",
            confirmLabel = "Delete",
            onConfirm = viewModel::onDeleteRoom,
            onDismiss = { confirmDeleteRoom = false },
        )
    }
    editingZone?.let { zone ->
        ZoneEditDialog(
            initial =
                ZoneForm(
                    id = zone.id,
                    roomId = room.id,
                    name = zone.name,
                    area = zone.areaFt2?.let(Format::days).orEmpty(),
                ),
            existing = zone.takeIf { it.id != 0L },
            onSave = {
                viewModel.onSaveZone(it)
                editingZone = null
            },
            onDismiss = { editingZone = null },
        )
    }
    deletingZone?.let { zone ->
        ConfirmDialog(
            title = "Delete zone ${zone.name}?",
            message = "Tasks in this zone are kept and become whole-room tasks.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.onDeleteZone(zone) },
            onDismiss = { deletingZone = null },
        )
    }
}
