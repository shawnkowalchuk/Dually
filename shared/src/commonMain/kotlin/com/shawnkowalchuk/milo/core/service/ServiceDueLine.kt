package com.shawnkowalchuk.milo.core.service

import com.shawnkowalchuk.milo.core.time.LocalDate
import kotlin.math.absoluteValue

// Which sentence says when a service item is due (since 2026-10-16; shared since 2026-11-14, for
// the iPhone's Service screen). Android writes it from its string resources, the iPhone from the
// same words in English: the choice of sentence is made here once, so the two cannot differ.

/** One of the due line's sentences, by the name of its string resource. */
enum class DueWording(val resourceName: String) {
    /** "Overdue by 400 km": [DueLine.km] is how far past. */
    OVERDUE_BY("service_overdue_by"),

    /** "Overdue since Apr 16, 2027": [DueLine.day] is the day it came due. */
    OVERDUE_SINCE("service_overdue_since"),

    /** "Due in 1,200 km or on Apr 16, 2027, whichever comes first". */
    DUE_IN_OR_ON("service_due_in_or_on"),

    /** "Due in 1,200 km". */
    DUE_IN("service_due_in"),

    /** "Due on Apr 16, 2027". */
    DUE_ON("service_due_on"),

    /** "Every 8,000 km. Type the odometer in Settings so Dually can count it." */
    NO_READING("service_due_no_reading"),
}

/**
 * When an item is due, as the sentence to write and what goes in it.
 *
 * @param km a distance in kilometres, for the sentences that name one.
 * @param day a day, for the sentences that name one.
 */
data class DueLine(val wording: DueWording, val km: Long?, val day: LocalDate?)

/**
 * When [item] is due, in one sentence: overdue first, by distance and then by time; then due in
 * a distance, on a day, or both, whichever comes first.
 */
fun dueLine(item: ServiceItem, due: ServiceDue): DueLine {
    val kmLeft = due.kmLeft
    val daysLeft = due.daysLeft
    val dueOn = due.dueOn
    return when {
        kmLeft != null && kmLeft <= 0 -> DueLine(DueWording.OVERDUE_BY, kmLeft.absoluteValue, null)

        daysLeft != null && daysLeft <= 0 && dueOn != null ->
            DueLine(DueWording.OVERDUE_SINCE, null, dueOn)

        kmLeft != null && dueOn != null -> DueLine(DueWording.DUE_IN_OR_ON, kmLeft, dueOn)

        kmLeft != null -> DueLine(DueWording.DUE_IN, kmLeft, null)

        dueOn != null -> DueLine(DueWording.DUE_ON, null, dueOn)

        // A distance to go by and nothing to count it from.
        else -> DueLine(DueWording.NO_READING, item.everyKm?.toLong() ?: 0, null)
    }
}
