package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.TripClassification
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.ZoneId

/** "Business", "Business, ran past schedule" or "Personal". */
internal fun TripClassification.inWords(): String = when {
    category == TripCategory.PERSONAL -> "Personal"
    ranPastSchedule -> "Business, ran past schedule"
    else -> "Business"
}

/** A category as the log names it. */
internal fun TripCategory?.inWords(): String = when (this) {
    TripCategory.BUSINESS -> "Business"
    TripCategory.PERSONAL -> "Personal"
    null -> "not sorted"
}

/**
 * What the schedule made of a trip and what it went by: "Business: it started on a Mon at
 * 08:14:05; that day's hours are 08:00 to 16:30 (America/Edmonton)".
 */
expect fun classificationText(
    classification: TripClassification,
    startedAtMs: Long,
    schedule: WorkSchedule,
    zone: ZoneId,
): String

/**
 * The line for one pass of the catch-up that sorted trips recorded earlier.
 *
 * @param reason what prompted the pass.
 * @param sorted what each trip that was sorted was made, by its id.
 * @param notSorted how many trips took nothing because they were no longer unsorted when the
 * write arrived, which happens if Shawn marks a trip by hand at that very moment.
 */
fun caughtUpText(
    reason: String,
    sorted: List<Pair<Long, TripClassification>>,
    notSorted: Int,
): String {
    val business = sorted.count { it.second.category == TripCategory.BUSINESS }
    val ranPast = sorted.count { it.second.ranPastSchedule }
    val personal = sorted.size - business
    val trips = if (sorted.size == 1) "1 trip" else "${sorted.size} trips"
    val skipped = if (notSorted == 0) "" else " $notSorted had been sorted in the meantime."
    return "Business or Personal set for $trips recorded before they were sorted ($reason), " +
        "by the work schedule as it is now: $business Business ($ranPast ran past schedule), " +
        "$personal Personal. No trip was discarded or otherwise changed.$skipped"
}
