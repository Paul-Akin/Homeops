package com.example.homeops.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.homeops.data.db.RoomEntity
import com.example.homeops.data.db.ZoneEntity
import com.example.homeops.domain.model.RoomType
import com.example.homeops.domain.model.displayName
import com.example.homeops.domain.validation.FieldResult
import com.example.homeops.domain.validation.FormValidation
import com.example.homeops.domain.validation.errorOrNull
import com.example.homeops.ui.common.DropdownField
import com.example.homeops.ui.common.FormTextField
import com.example.homeops.ui.common.Format

/** Raw text of the room form plus the parse step. Plain data, so it can be unit-tested without Compose. */
data class RoomForm(
    val id: Long = 0,
    val name: String = "",
    val roomType: RoomType = RoomType.OTHER,
    val floor: String = "1",
    val area: String = "",
    val weight: String = "1",
    val sortOrder: Int = 0,
) {
    data class Errors(
        val name: String?,
        val floor: String?,
        val area: String?,
        val weight: String?,
    )

    fun errors(): Errors =
        Errors(
            name = FormValidation.name(name).errorOrNull,
            floor = FormValidation.floor(floor).errorOrNull,
            area = FormValidation.optionalAreaFt2(area).errorOrNull,
            weight = FormValidation.roomWeight(weight).errorOrNull,
        )

    /** Returns the entity, or null if any field is invalid. */
    fun toEntityOrNull(): RoomEntity? {
        val n = FormValidation.name(name) as? FieldResult.Ok<String> ?: return null
        val f = FormValidation.floor(floor) as? FieldResult.Ok<Int> ?: return null
        val a = FormValidation.optionalAreaFt2(area) as? FieldResult.Ok<Double?> ?: return null
        val w = FormValidation.roomWeight(weight) as? FieldResult.Ok<Double> ?: return null
        return RoomEntity(
            id = id,
            name = n.value,
            roomType = roomType,
            floor = f.value,
            areaFt2 = a.value,
            weight = w.value,
            sortOrder = sortOrder,
        )
    }

    companion object {
        fun from(room: RoomEntity) =
            RoomForm(
                id = room.id,
                name = room.name,
                roomType = room.roomType,
                floor = room.floor.toString(),
                area = room.areaFt2?.let(Format::days).orEmpty(),
                weight = Format.days(room.weight),
                sortOrder = room.sortOrder,
            )
    }
}

data class ZoneForm(
    val id: Long = 0,
    val roomId: Long,
    val name: String = "",
    val area: String = "",
) {
    fun errors(): Pair<String?, String?> =
        FormValidation.name(name).errorOrNull to FormValidation.optionalAreaFt2(area).errorOrNull

    fun toEntityOrNull(existing: ZoneEntity?): ZoneEntity? {
        val n = FormValidation.name(name) as? FieldResult.Ok<String> ?: return null
        val a = FormValidation.optionalAreaFt2(area) as? FieldResult.Ok<Double?> ?: return null
        return (existing ?: ZoneEntity(roomId = roomId, name = n.value)).copy(name = n.value, areaFt2 = a.value)
    }
}

@Composable
fun RoomEditDialog(
    initial: RoomForm,
    onSave: (RoomEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember { mutableStateOf(initial) }
    // Errors are shown only after the first save attempt, so an empty new form isn't all red.
    var showErrors by remember { mutableStateOf(false) }
    val errors = form.errors()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add room" else "Edit room") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FormTextField(form.name, { form = form.copy(name = it) }, "Name", errors.name.takeIf { showErrors })
                DropdownField(
                    label = "Type",
                    selected = form.roomType,
                    options = RoomType.entries,
                    optionLabel = { it.displayName() },
                    onSelect = { form = form.copy(roomType = it) },
                )
                FormTextField(
                    form.floor,
                    { form = form.copy(floor = it) },
                    "Floor",
                    errors.floor.takeIf { showErrors },
                    numeric = true,
                )
                FormTextField(
                    form.area,
                    { form = form.copy(area = it) },
                    "Area (ft², optional)",
                    errors.area.takeIf { showErrors },
                    numeric = true,
                )
                FormTextField(
                    form.weight,
                    { form = form.copy(weight = it) },
                    "Priority weight",
                    errors.weight.takeIf { showErrors },
                    numeric = true,
                    supportingText = "1 = normal; 2 = twice as important",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val entity = form.toEntityOrNull()
                if (entity == null) showErrors = true else onSave(entity)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ZoneEditDialog(
    initial: ZoneForm,
    existing: ZoneEntity?,
    onSave: (ZoneEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember { mutableStateOf(initial) }
    var showErrors by remember { mutableStateOf(false) }
    val (nameError, areaError) = form.errors()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add zone" else "Edit zone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FormTextField(
                    form.name,
                    { form = form.copy(name = it) },
                    "Name (e.g. Countertop left)",
                    nameError.takeIf { showErrors },
                )
                FormTextField(
                    form.area,
                    { form = form.copy(area = it) },
                    "Area (ft², optional)",
                    areaError.takeIf { showErrors },
                    numeric = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val entity = form.toEntityOrNull(existing)
                if (entity == null) showErrors = true else onSave(entity)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
