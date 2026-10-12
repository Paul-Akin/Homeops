package com.example.homeops.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homeops.ui.common.SectionHeader
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private enum class TimeField { DEFAULT, QUIET_START, QUIET_END }

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun formatMinute(minute: Int): String = LocalTime.of(minute / 60, minute % 60).format(timeFormat)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<TimeField?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    // Android 13+ asks for notification permission at runtime. This launcher shows the system
    // prompt and reports the user's answer back to us.
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionDenied = !granted
            if (granted) viewModel.setEnabled(true)
        }

    fun enableReminders() {
        val needsPrompt =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPrompt) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setEnabled(true)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (state.isLoading) return@Scaffold
        val r = state.reminders
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Reminders")
            ListItem(
                headlineContent = { Text("Daily reminder") },
                supportingContent = { Text("One digest a day when tasks are due, plus at most one overdue nudge") },
                trailingContent = {
                    Switch(
                        checked = r.enabled,
                        onCheckedChange = { on -> if (on) enableReminders() else viewModel.setEnabled(false) },
                    )
                },
            )
            if (permissionDenied) {
                Text(
                    "Notifications are blocked. Allow them for HomeOps in system Settings > Apps.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (r.enabled) {
                NextReminderCard(state)
            }
            HorizontalDivider()
            TimeRow("Default reminder time", r.defaultMinute, "Used until your own routine is learned") {
                editing = TimeField.DEFAULT
            }
            TimeRow("Quiet hours start", r.quietStartMinute, null) { editing = TimeField.QUIET_START }
            TimeRow(
                "Quiet hours end",
                r.quietEndMinute,
                "Reminders that would land in quiet hours wait until this time",
            ) {
                editing = TimeField.QUIET_END
            }
            ListItem(
                headlineContent = { Text("Separate weekday and weekend times") },
                supportingContent = { Text("Learn when you clean on weekdays and on weekends independently") },
                trailingContent = {
                    Switch(checked = r.splitWeekdayWeekend, onCheckedChange = viewModel::setSplitWeekdayWeekend)
                },
            )
        }
    }

    editing?.let { field ->
        val initial =
            when (field) {
                TimeField.DEFAULT -> state.reminders.defaultMinute
                TimeField.QUIET_START -> state.reminders.quietStartMinute
                TimeField.QUIET_END -> state.reminders.quietEndMinute
            }
        TimePickerDialog(
            initialMinute = initial,
            onConfirm = { minute ->
                when (field) {
                    TimeField.DEFAULT -> viewModel.setDefaultMinute(minute)
                    TimeField.QUIET_START -> viewModel.setQuietStart(minute)
                    TimeField.QUIET_END -> viewModel.setQuietEnd(minute)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun NextReminderCard(state: SettingsUiState) {
    val next = state.nextReminder ?: return
    val whenText =
        next.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())) + " " +
            next.toLocalTime().format(timeFormat)
    val why =
        if (state.learned) {
            "Learned from ${state.samples} recent completions (consistency R = ${"%.2f".format(state.consistency)}), " +
                "30 min before your usual time"
        } else {
            "Default time. After 5+ completions at a consistent time of day, HomeOps learns your routine."
        }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Next reminder: $whenText", style = MaterialTheme.typography.titleMedium)
        Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimeRow(
    label: String,
    minute: Int,
    help: String?,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent =
            if (help != null) {
                { Text(help) }
            } else {
                null
            },
        trailingContent = { Text(formatMinute(minute), style = MaterialTheme.typography.titleMedium) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinute: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = pickerState) },
    )
}
