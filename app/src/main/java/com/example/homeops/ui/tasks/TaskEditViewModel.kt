package com.example.homeops.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.homeops.data.db.CompletionLogRow
import com.example.homeops.data.db.ZoneEntity
import com.example.homeops.data.repo.HomeRepository
import com.example.homeops.data.repo.TaskDraft
import com.example.homeops.data.repo.TaskRepository
import com.example.homeops.domain.Tuning
import com.example.homeops.domain.estimation.PriorEstimate
import com.example.homeops.domain.model.TaskType
import com.example.homeops.domain.scheduling.Dueness
import com.example.homeops.domain.validation.FieldResult
import com.example.homeops.domain.validation.FormValidation
import com.example.homeops.domain.validation.errorOrNull
import com.example.homeops.ui.common.Format
import com.example.homeops.ui.nav.TaskEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Raw form text. Parsing is delegated to the domain's FormValidation. */
data class TaskForm(
    val title: String = "",
    val taskType: TaskType = TaskType.WIPE,
    val zoneId: Long? = null,
    val area: String = "",
    val frequencyDays: String = Format.days(Tuning.FREQUENCY_DAYS_DEFAULT),
    val severity: Int = Tuning.SEVERITY_DEFAULT,
    val isActive: Boolean = true,
) {
    data class Errors(
        val title: String?,
        val area: String?,
        val frequency: String?,
    )

    fun errors() =
        Errors(
            title = FormValidation.name(title).errorOrNull,
            area = FormValidation.optionalAreaFt2(area).errorOrNull,
            frequency = FormValidation.frequencyDays(frequencyDays).errorOrNull,
        )

    fun toDraftOrNull(
        id: Long,
        roomId: Long,
    ): TaskDraft? {
        val t = FormValidation.name(title) as? FieldResult.Ok<String> ?: return null
        val a = FormValidation.optionalAreaFt2(area) as? FieldResult.Ok<Double?> ?: return null
        val f = FormValidation.frequencyDays(frequencyDays) as? FieldResult.Ok<Double> ?: return null
        return TaskDraft(
            id = id,
            roomId = roomId,
            zoneId = zoneId,
            title = t.value,
            taskType = taskType,
            areaFt2 = a.value,
            severity = FormValidation.severity(severity),
            frequencyDays = f.value,
            isActive = isActive,
        )
    }

    /** Live t0 preview; null while the area field is invalid. */
    fun priorMinutesOrNull(): Double? {
        val a = FormValidation.optionalAreaFt2(area) as? FieldResult.Ok<Double?> ?: return null
        return PriorEstimate.priorMinutes(taskType, a.value, severity)
    }
}

data class TaskEditUiState(
    val isNew: Boolean = true,
    val isLoaded: Boolean = false,
    val form: TaskForm = TaskForm(),
    val showErrors: Boolean = false,
    val zones: List<ZoneEntity> = emptyList(),
    val history: List<CompletionLogRow> = emptyList(),
    /** True once saved or deleted; the screen then navigates back. */
    val isDone: Boolean = false,
)

private data class EditorState(
    val isLoaded: Boolean,
    val form: TaskForm,
    val showErrors: Boolean = false,
    val isDone: Boolean = false,
)

@HiltViewModel
class TaskEditViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        home: HomeRepository,
        private val tasks: TaskRepository,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<TaskEditRoute>()
        private val isNew = route.taskId == 0L

        private val editor = MutableStateFlow(EditorState(isLoaded = isNew, form = TaskForm()))

        init {
            if (!isNew) {
                viewModelScope.launch {
                    val task = tasks.getTask(route.taskId)
                    if (task == null) {
                        editor.update { it.copy(isDone = true) }
                        return@launch
                    }
                    val form =
                        TaskForm(
                            title = task.title,
                            taskType = task.taskType,
                            zoneId = task.zoneId,
                            area = task.areaFt2?.let(Format::days).orEmpty(),
                            frequencyDays = Format.days(Dueness.frequencyDaysFromTau(task.tauDays)),
                            severity = task.severity,
                            isActive = task.isActive,
                        )
                    editor.update { it.copy(isLoaded = true, form = form) }
                }
            }
        }

        val state: StateFlow<TaskEditUiState> =
            combine(
                editor,
                home.observeZones(route.roomId),
                if (isNew) flowOf(emptyList<CompletionLogRow>()) else tasks.observeCompletionsForTask(route.taskId),
            ) { ed, zones, history ->
                TaskEditUiState(
                    isNew = isNew,
                    isLoaded = ed.isLoaded,
                    form = ed.form,
                    showErrors = ed.showErrors,
                    zones = zones,
                    history = history,
                    isDone = ed.isDone,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskEditUiState(isNew = isNew))

        /** Every field edit funnels through here: `transform` returns the updated form copy. */
        fun onFormChange(transform: (TaskForm) -> TaskForm) {
            editor.update { it.copy(form = transform(it.form)) }
        }

        fun onSave() {
            val draft = editor.value.form.toDraftOrNull(route.taskId, route.roomId)
            if (draft == null) {
                editor.update { it.copy(showErrors = true) }
                return
            }
            viewModelScope.launch {
                tasks.saveTask(draft)
                editor.update { it.copy(isDone = true) }
            }
        }

        fun onDelete() {
            if (isNew) return
            viewModelScope.launch {
                tasks.deleteTask(route.taskId)
                editor.update { it.copy(isDone = true) }
            }
        }
    }
