package com.example.homeops.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homeops.domain.Tuning
import com.example.homeops.domain.model.TaskType
import com.example.homeops.domain.model.displayName
import com.example.homeops.ui.common.ConfirmDialog
import com.example.homeops.ui.common.DropdownField
import com.example.homeops.ui.common.FormTextField
import com.example.homeops.ui.common.Format
import com.example.homeops.ui.common.SectionHeader
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    onDone: () -> Unit,
    viewModel: TaskEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New task" else "Edit task") },
                navigationIcon = {
                    IconButton(
                        onClick = onDone,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete task")
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.isLoaded) return@Scaffold
        val form = state.form
        val errors = form.errors()
        val show = state.showErrors
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormTextField(
                form.title,
                { v -> viewModel.onFormChange { it.copy(title = v) } },
                "Title",
                errors.title.takeIf { show },
            )
            DropdownField(
                label = "Task type",
                selected = form.taskType,
                options = TaskType.entries,
                optionLabel = { it.displayName() },
                onSelect = { v -> viewModel.onFormChange { it.copy(taskType = v) } },
            )
            // Zone choices: null = whole room, then the room's zones.
            val zoneOptions: List<Long?> = listOf(null) + state.zones.map { it.id }
            DropdownField(
                label = "Zone",
                selected = form.zoneId,
                options = zoneOptions,
                optionLabel = { id -> state.zones.firstOrNull { it.id == id }?.name ?: "Whole room" },
                onSelect = { v -> viewModel.onFormChange { it.copy(zoneId = v) } },
            )
            FormTextField(
                form.frequencyDays,
                { v -> viewModel.onFormChange { it.copy(frequencyDays = v) } },
                "Every N days",
                errors.frequency.takeIf { show },
                numeric = true,
                supportingText = "Becomes due after this many days",
            )
            FormTextField(
                form.weight,
                { v -> viewModel.onFormChange { it.copy(weight = v) } },
                "Priority weight",
                errors.weight.takeIf { show },
                numeric = true,
                supportingText = "1 = normal; higher sorts it earlier on Today",
            )
            FormTextField(
                form.area,
                { v -> viewModel.onFormChange { it.copy(area = v) } },
                "Area (ft², optional)",
                errors.area.takeIf { show },
                numeric = true,
                supportingText = "Blank uses a typical area for the task type",
            )

            Text("Typical soil level: ${form.severity} of 5", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = form.severity.toFloat(),
                onValueChange = { v -> viewModel.onFormChange { it.copy(severity = v.roundToInt()) } },
                valueRange = Tuning.SEVERITY_MIN.toFloat()..Tuning.SEVERITY_MAX.toFloat(),
                steps = Tuning.SEVERITY_MAX - Tuning.SEVERITY_MIN - 1,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active (shows on Today)", Modifier.weight(1f))
                Switch(
                    checked = form.isActive,
                    onCheckedChange = { v -> viewModel.onFormChange { it.copy(isActive = v) } },
                )
            }

            form.priorMinutesOrNull()?.let { minutes ->
                Text(
                    "Estimated time: about ${Format.minutes(minutes)} " +
                        "(table estimate; learns from your timings in a later update)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(onClick = viewModel::onSave, modifier = Modifier.fillMaxWidth()) { Text("Save") }

            if (!state.isNew) {
                Spacer(Modifier.padding(4.dp))
                SectionHeader("History (${state.history.size})")
                if (state.history.isEmpty()) Text("Not completed yet.")
                state.history.take(50).forEach { row ->
                    Text("${Format.dateTime(row.completedAt)}${if (row.wasOnTime) "" else "  (late)"}")
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete task?",
            message =
                "This also deletes its ${state.history.size} completion record(s). " +
                    "To keep history, turn off Active instead.",
            confirmLabel = "Delete",
            onConfirm = viewModel::onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}
