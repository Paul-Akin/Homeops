package com.example.homeops.domain.estimation

import com.example.homeops.domain.Tuning
import com.example.homeops.domain.model.TaskType

/**
 * Prior duration estimate t0 = a_k + b_k * A * s (DESIGN.md Section 5.1).
 *
 * M1 needs this to populate TaskEntity.estMinutesPrior for manually created tasks.
 * Correction factors, uncertainty, and T80 arrive in M3 (TimeEstimator).
 */
object PriorEstimate {

    /** s = 0.6 + 0.2 * (severity - 1), with severity clamped to 1..5, so s in [0.6, 1.4]. */
    fun severityFactor(severity: Int): Double {
        val sev = severity.coerceIn(Tuning.SEVERITY_MIN, Tuning.SEVERITY_MAX)
        return Tuning.SEVERITY_BASE + Tuning.SEVERITY_STEP * (sev - 1)
    }

    /**
     * Table-based prior in minutes. A null [areaFt2] uses the task type's default area.
     * The table itself is not clamped (DESIGN.md clamps only AI-supplied estimates).
     */
    fun priorMinutes(taskType: TaskType, areaFt2: Double?, severity: Int): Double {
        val row = Tuning.priorTable.getValue(taskType)
        val area = areaFt2 ?: row.defaultAreaFt2
        require(area >= 0.0 && area.isFinite()) { "areaFt2 must be a finite, non-negative number" }
        return row.setupMin + row.minPerFt2 * area * severityFactor(severity)
    }
}
