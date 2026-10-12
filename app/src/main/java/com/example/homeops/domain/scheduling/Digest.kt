package com.example.homeops.domain.scheduling

import com.example.homeops.domain.Tuning
import kotlin.math.roundToInt

/** One due task as the notification sees it. */
data class DigestItem(
    val title: String,
    val estMinutes: Double,
    val dueness: Double,
    val priority: Double,
)

data class NotificationText(
    val title: String,
    val body: String,
)

/**
 * Notification wording (DESIGN.md Section 8): "4 tasks due, about 35 min".
 * The minutes are the sum of prior estimates for now; M3 switches to T80.
 */
object Digest {
    private const val NAMES_SHOWN = 3

    /** Null when nothing is due, so no notification is posted. */
    fun digest(due: List<DigestItem>): NotificationText? {
        if (due.isEmpty()) return null
        val minutes = due.sumOf { it.estMinutes }.roundToInt().coerceAtLeast(1)
        val noun = if (due.size == 1) "task" else "tasks"
        return NotificationText("${due.size} $noun due, about $minutes min", names(due))
    }

    /** Overdue = D above the on-time limit (0.9). Null when nothing is overdue. */
    fun overdueNudge(items: List<DigestItem>): NotificationText? {
        val overdue = items.filter { it.dueness > Tuning.ON_TIME_THRESHOLD }
        if (overdue.isEmpty()) return null
        val noun = if (overdue.size == 1) "task is" else "tasks are"
        return NotificationText("${overdue.size} $noun overdue", names(overdue))
    }

    private fun names(items: List<DigestItem>): String {
        val sorted = items.sortedByDescending { it.priority }
        val shown = sorted.take(NAMES_SHOWN).joinToString(", ") { it.title }
        val more = sorted.size - NAMES_SHOWN
        return if (more > 0) "$shown, and $more more" else shown
    }
}
