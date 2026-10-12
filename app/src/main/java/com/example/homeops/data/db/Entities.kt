package com.example.homeops.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.homeops.domain.Tuning
import com.example.homeops.domain.model.RoomType
import com.example.homeops.domain.model.TaskSource
import com.example.homeops.domain.model.TaskType
import java.time.Instant

// Room entities for M1 (DESIGN.md Sections 4.1, 4.2). Naming note: "Room" the Jetpack
// database library and "RoomEntity" the household room are unrelated; the clash is unfortunate
// but RoomEntity keeps the design's vocabulary. Containers, items, photos, snoozes, correction
// factors, and badges are added in later milestones, each with a migration.

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val roomType: RoomType,
    val floor: Int = 1,
    val areaFt2: Double? = null,
    val weight: Double = 1.0,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "zones",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("roomId")],
)
data class ZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val name: String,
    val areaFt2: Double? = null,
    /** Optional position on a 2D floor plan, normalized 0..1 (used in M7). */
    val mapX: Float? = null,
    val mapY: Float? = null,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE,
        ),
        // Deleting a zone keeps its tasks; they fall back to "whole room".
        ForeignKey(
            entity = ZoneEntity::class,
            parentColumns = ["id"],
            childColumns = ["zoneId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("roomId"), Index("zoneId")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val zoneId: Long? = null,
    val title: String,
    val taskType: TaskType,
    val areaFt2: Double? = null,
    /** 1..5, last observed soil level. */
    val severity: Int = Tuning.SEVERITY_DEFAULT,
    /** t0 in minutes, from the prior table or AI. */
    val estMinutesPrior: Double,
    val sigmaLn: Double = Tuning.SIGMA_LN_DEFAULT,
    /** Decay time constant tau in days; tau = F / ln 5 for a nominal frequency of F days. */
    val tauDays: Double,
    val lastCompletedAt: Instant? = null,
    val createdAt: Instant,
    val source: TaskSource = TaskSource.MANUAL,
    val isActive: Boolean = true,
    val supplies: List<String> = emptyList(),
)

/** Append-only completion log (DESIGN.md Section 4.2). */
@Entity(
    tableName = "completion_events",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId"), Index("completedAt")],
)
data class CompletionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val completedAt: Instant,
    /** Null when the timer was not used (always null in M1; the timer arrives in M3). */
    val actualMinutes: Double? = null,
    val estMinutesAtStart: Double? = null,
    /** Photo links; the photos table arrives in M4, so these are plain columns for now. */
    val beforePhotoId: Long? = null,
    val afterPhotoId: Long? = null,
    val wasOnTime: Boolean,
)
