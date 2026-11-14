package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.schedule.NOT_FILED
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.schedule.refileTrip
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.editedTrip

// What the edit screen was opened with, and who chose the Business or Personal it shows: the
// same on both phones (shared since 2026-11-13, for the iPhone's edit screen).

/** Who chose the Business or Personal the form shows. The line under the two choices says it. */
enum class KindSource {
    /** Shawn: he pressed one in this form, or the trip was set by hand before. */
    BY_YOU,

    /** MilO, from the work schedule and the start time the form shows. */
    BY_SCHEDULE,

    /** Nobody anew: it is what the trip was saved as, and its start has not been changed. */
    AS_SAVED,

    /** Nobody yet: a trip is being added and has no start time to sort it by. */
    NEEDS_START,

    /** Nobody can: the trip is not sorted, or the work schedule cannot be read. */
    NOT_KNOWN,
}

/**
 * What the form was opened with. It stays the same for as long as the screen is open.
 *
 * @param stored the trip that is being edited, as it was read when the form opened, or null
 * when a trip is being added.
 * @param schedule the work schedule, or null if the settings could not be read.
 * @param zone the phone's time zone. The form reads and writes every time in it.
 * @param openedAtMs when the form was opened: the day no date may lie after, and where the
 * clock dial starts for a time that has not been chosen.
 * @param unit the unit chosen in Settings when the form was opened: the distance is shown and
 * typed in it for as long as the screen is open.
 */
data class EditSession(
    val stored: Trip?,
    val schedule: WorkSchedule?,
    val zone: ZoneId,
    val openedAtMs: Long,
    val unit: DistanceUnit,
) {
    /** The form as it is when the screen opens: the stored trip's, or the empty one. */
    fun openedForm(): TripForm =
        if (stored == null) blankForm(openedAtMs, zone, unit) else formFor(stored, zone, unit)
}

/**
 * The Business or Personal a save of [form] would store, and who chose it. It is worked out by
 * the functions the save itself uses (`editedTrip`, `refileTrip`), so the form cannot show one
 * thing and store another.
 */
fun kindShown(form: TripForm, session: EditSession): Pair<TripCategory?, KindSource> {
    val stored = session.stored
    val zone = session.zone
    val start = form.startedAtMs(stored, zone)
    if (stored != null) {
        val after = editedTrip(stored, form.toEdit(stored, zone), session.schedule, zone)
        val source =
            when {
                after.categorySetByHand -> KindSource.BY_YOU
                after.category == null -> KindSource.NOT_KNOWN
                start != stored.startedAtMs -> KindSource.BY_SCHEDULE
                else -> KindSource.AS_SAVED
            }
        return after.category to source
    }
    val chosen = form.chosenCategory
    if (chosen != null) return chosen to KindSource.BY_YOU
    if (start == null) return null to KindSource.NEEDS_START
    val filed =
        refileTrip(
            stored = NOT_FILED,
            startChanged = true,
            endChanged = true,
            startedAtMs = start,
            endedAtMs = form.endedAtMs(stored = null, zone),
            chosen = null,
            schedule = session.schedule,
            zone = zone,
        )
    val source = if (filed.category == null) KindSource.NOT_KNOWN else KindSource.BY_SCHEDULE
    return filed.category to source
}
