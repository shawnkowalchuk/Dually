package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.schedule.TripFiling
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.formatTimeWithSeconds
import com.shawnkowalchuk.milo.core.trip.ANOTHER_VEHICLE_WITHIN_METRES
import com.shawnkowalchuk.milo.core.trip.ClosedTrip
import com.shawnkowalchuk.milo.core.trip.TripEndReason
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.WaitingEnd
import com.shawnkowalchuk.milo.data.trip.classificationText
import kotlin.math.roundToInt

// The event log's words for what the trip ledger writes (TripLedger), shared with the iPhone
// since phase 4b. The rest of the trip log's words stay with Android's trip code (TripLogText).

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * The line that records a closed trip: what became of it, why, and what it was measured from.
 *
 * @param ignored true if the trip rules kept the trip and it is stored as discarded all the
 * same, because it started outside the work schedule and such trips are set to be ignored.
 * @param parked for a trip the parked rule closed, when the truck last moved ([parkedText]).
 */
fun closedText(
    closed: ClosedTrip,
    reason: TripEndReason,
    storedFixes: Int,
    minimumTripDistanceMetres: Int,
    ignored: Boolean,
    parked: String? = null,
): String {
    val metres = closed.distance.metres.roundToInt()
    val outcome =
        when {
            ignored ->
                "discarded: it started outside the work schedule, and trips outside it are " +
                    "set to be ignored ($metres m). It can be counted on the Trips screen"

            closed.status != TripStatus.DISCARDED -> "finished, $metres m"

            reason == TripEndReason.FALSE_START ->
                "discarded as a false start: the truck's connection was never confirmed ($metres m)"

            else -> "discarded: $metres m is under the minimum of $minimumTripDistanceMetres m"
        }
    val distance = closed.distance
    val fixes =
        "$storedFixes fixes stored, ${distance.acceptedCount} used, " +
            "${distance.rejectedForAccuracy} too inaccurate, ${distance.rejectedAsJump} jumps"
    return "$outcome; ended by $reason${parked.orEmpty()}; $fixes"
}

/**
 * A trip removed for good as a drive in another vehicle (`leftInAnotherVehicle`): what it was
 * removed for, with the figures that decided it, so the log keeps what the trips table no longer
 * holds.
 */
fun anotherVehicleText(closed: ClosedTrip, startedAtMs: Long, zone: ZoneId): String {
    val metres = closed.distance.metres.roundToInt()
    val within = ANOTHER_VEHICLE_WITHIN_METRES.roundToInt()
    val started = formatTimeWithSeconds(startedAtMs, zone)
    val lost = formatTimeWithSeconds(closed.endedAtMs, zone)
    return "removed for good as a drive in another vehicle: it started at $started when the " +
        "parked truck seemed to drive off, and the truck's connection was lost for good at " +
        "$lost, $metres m on, under $within m. Its row and its points are deleted"
}

/**
 * Why the parked rule closed a trip, to follow its reason in [closedText]: when the truck last
 * moved, which is where the trip was cut, and the limit it stood still for.
 */
fun parkedText(lastMovedAtMs: Long, parkedLimitMs: Long?, zone: ZoneId): String {
    val lastMoved = formatTimeWithSeconds(lastMovedAtMs, zone)
    val limit = parkedLimitMs?.let { " for ${it / MILLIS_PER_MINUTE} min" }.orEmpty()
    return " (the truck was parked: it last moved at $lastMoved and then stood still$limit)"
}

/**
 * The line for a trip that has started.
 *
 * @param fromParked true if the truck, connected and parked, moved again.
 * @param carriedOver how many points a trip that begins at the parked place was given: the
 * place and the fixes the watch kept. Null for a trip that begins where it was started.
 */
fun startedText(
    tripId: Long,
    startedBy: TripStartCause,
    fromParked: Boolean,
    carriedOver: Int?,
): String {
    val started = "Trip $tripId started by $startedBy"
    return when {
        fromParked ->
            "$started: it was connected and parked, and it moved. The trip starts where it " +
                "was parked ($carriedOver points carried over), with no connect sound"

        carriedOver != null ->
            "$started while Dually waited beside the parked truck. The trip starts where it " +
                "was parked ($carriedOver points carried over)"

        else -> started
    }
}

/** The line for the beginning of a wait beside the parked truck. */
const val WAITING_BEGAN =
    "Waiting for the truck to move: it is still connected. Its position is read at a low " +
        "rate, and a trip starts when it moves"

/** The line for the end of a wait beside the parked truck, with the reason. */
fun waitingEndedText(reason: WaitingEnd): String {
    val why =
        when (reason) {
            WaitingEnd.MOVED -> "it moved"

            WaitingEnd.TRUCK_DISCONNECTED -> "it is no longer connected. Nothing was recorded"

            WaitingEnd.NEW_LINK -> "a new link to it was reported, which starts a trip"

            WaitingEnd.START_PRESSED -> "Start was pressed"

            WaitingEnd.END_PRESSED -> "End was pressed"

            WaitingEnd.TIME_LIMIT ->
                "it has stood too long, and Dually has stopped watching it to spare the battery. " +
                    "The next trip starts when the truck reconnects, when Dually is opened or " +
                    "restarted, or with Start"

            WaitingEnd.TRIP_OPEN -> "a trip is open"
        }
    return "No longer waiting for the truck to move: $why"
}

/**
 * What the work schedule made of a trip that has just been closed, to follow [closedText] in
 * the same line: Business or Personal, and the day, the time and the hours it was judged by.
 *
 * @param kept false for a trip that is stored as discarded, by the trip rules or because it is
 * ignored. It is sorted all the same, for the day it is counted after all, but the line does
 * not say "saved".
 * @param schedule the schedule it was judged by, or null if the settings could not be read.
 */
fun filedText(
    filing: TripFiling,
    kept: Boolean,
    startedAtMs: Long,
    schedule: WorkSchedule?,
    zone: ZoneId,
): String {
    val classification = filing.classification
    if (classification == null || schedule == null) {
        return "not sorted into Business or Personal: the settings cannot be read. " +
            "It is sorted at a later start of Dually"
    }
    val verb = if (kept) "saved as" else "if it is counted, it is"
    return "$verb ${classificationText(classification, startedAtMs, schedule, zone)}"
}
