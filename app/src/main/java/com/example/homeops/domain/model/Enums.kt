package com.example.homeops.domain.model

// Domain enums (DESIGN.md Section 4). Room stores enums by `name`, so never rename
// a constant without a migration; adding new constants at the end is safe.

enum class RoomType { KITCHEN, BATHROOM, BEDROOM, LIVING, LAUNDRY, GARAGE, OFFICE, HALLWAY, OTHER }

enum class TaskType { WIPE, VACUUM, MOP, DUST, SCRUB, DECLUTTER, LAUNDRY, DISHES, TRASH, OTHER }

enum class TaskSource { MANUAL, AI_PHOTO, AI_PLAN }

/** "KITCHEN" -> "Kitchen", "AI_PHOTO" -> "Ai photo". Pure string helper for display labels. */
fun Enum<*>.displayName(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
