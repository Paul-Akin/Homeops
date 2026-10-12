package com.example.homeops.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.homeops.domain.Tuning
import com.example.homeops.ui.theme.StatusColors
import kotlin.math.roundToInt

/**
 * Read-only dropdown for picking one of [options]. Generic over T so it works for enums,
 * zones, etc.; [label] turns an option into display text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Text field with an inline validation error. */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    supportingText: String? = null,
) {
    val message = error ?: supportingText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText =
            if (message != null) {
                { Text(message) }
            } else {
                null
            },
        singleLine = true,
        keyboardOptions =
            if (numeric) {
                KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                )
            } else {
                KeyboardOptions.Default
            },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Horizontal bar showing due-ness D in [0, 1]. Green below D*, orange from D* (due),
 * red above the on-time limit (0.9). Thresholds come from Tuning, not hard-coded.
 */
@Composable
fun DuenessBar(
    d: Double,
    modifier: Modifier = Modifier,
) {
    val color =
        when {
            d > Tuning.ON_TIME_THRESHOLD -> StatusColors.Overdue
            d >= Tuning.DUE_THRESHOLD -> StatusColors.Due
            else -> StatusColors.OnTrack
        }
    LinearProgressIndicator(
        progress = { d.toFloat().coerceIn(0f, 1f) },
        color = color,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Due-ness ${(d * 100).roundToInt()} percent" },
    )
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}
