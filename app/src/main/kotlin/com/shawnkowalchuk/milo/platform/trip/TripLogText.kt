package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.schedule.TripFiling
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.trip.ANOTHER_VEHICLE_WITHIN_METRES
import com.shawnkowalchuk.milo.core.trip.ClosedTrip
import com.shawnkowalchuk.milo.core.trip.TripEndReason
import com.shawnkowalchuk.milo.core.trip.TripEvent
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.WaitingEnd
import com.shawnkowalchuk.milo.data.trip.classificationText
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// How the trip controller words its event-log lines. Plain functions, kept apart from the
// controller so that what is logged can be read (and tested) in one place. The event log is
// English only: it is evidence for diagnosing a missed trip, not a screen.

/** The state in words: what the trip is doing, and what is believed about the two connections. */
internal fun TripState.describe(): String {
    val open = trip
    val tripText =
        when {
            open == null -> "no trip"
            open.confirmByMs != null -> "trip open, waiting for the truck to be confirmed"
            open.grace != null -> "trip open, in its grace period"
            !open.truckSeen -> "trip open, truck not seen in it"
            else -> "trip open"
        }
    val truckText = if (truckConnected) "truck connected" else "truck not connected"
    val autoText =
        if (androidAutoConnected) "Android Auto connected" else "Android Auto not connected"
    val holdOffText = if (autoStartHeldOff) "; automatic start held off" else ""
    val parkedText =
        when (parked?.watching) {
            null -> ""
            true -> "; waiting for the parked truck to move"
            false -> "; the truck is parked and no longer watched"
        }
    return "$tripText; $truckText; $autoText$holdOffText$parkedText"
}

/** What an event told the trip rules, in words. */
internal fun TripEvent.describe(): String = when (this) {
    is TripEvent.TruckConnection ->
        if (connected) "the truck is connected" else "the truck is not connected"

    is TripEvent.TruckLinkConnected -> "a link to the truck was made"

    is TripEvent.TruckAppeared -> "the companion service says the truck appeared"

    is TripEvent.AndroidAutoConnection ->
        if (connected) "Android Auto is connected" else "Android Auto is not connected"

    is TripEvent.ManualStart -> "Start pressed, ${connectedText(truckConnected)}"

    is TripEvent.ManualEnd -> "End pressed, ${connectedText(truckConnected)}"

    is TripEvent.Moved -> "the truck moved"

    is TripEvent.MoveTakenBack -> "the movement last counted was one bad GPS fix"
}

private fun connectedText(truckConnected: Boolean): String =
    if (truckConnected) "truck connected" else "truck not connected"

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * The line for GPS going off beside the parked truck once the wait has lasted an hour
 * (`parkedGpsUntilMs`). The trip service writes it.
 */
internal const val PARKED_GPS_OFF =
    "GPS is off beside the parked truck, to spare the battery. The phone's motion sensor " +
        "watches instead: getting into a vehicle turns GPS on again"

private const val MILLIS_PER_SECOND = 1000L

/**
 * The line for a sign of driving that turns GPS on beside the parked truck.
 *
 * @param ageMs how long before now the sign came.
 * @param forMs how long from now GPS is read.
 */
internal fun gpsForDrivingText(
    ageMs: Long,
    forMs: Long,
    sign: DrivingSign = DrivingSign.VEHICLE_ENTERED,
): String {
    val minutes = (forMs + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE
    val seconds = ageMs / MILLIS_PER_SECOND
    val what =
        when (sign) {
            DrivingSign.VEHICLE_ENTERED -> "The phone reports getting into a vehicle $seconds s ago"

            DrivingSign.ANDROID_AUTO -> "Android Auto connected $seconds s ago"

            DrivingSign.LEFT_PARKED_PLACE ->
                "The phone left the parked truck's place $seconds s ago (Android's geofence, " +
                    "${PARKED_FENCE_RADIUS_METRES.toInt()} m)"
        }
    return "$what: the parked truck's position is read every 5 s for the next $minutes min, " +
        "and a trip starts if it drives off"
}

/** The line for a start of the trip service that failed, at the preflight or in Android. */
internal fun couldNotStartText(request: StartRequest?, failure: StartFailure): String {
    val forWhat = request?.let { "${it.source}: ${it.trigger}" } ?: "a restart by Android"
    val why = failure.problems.joinToString().ifEmpty { "Android refused the foreground service" }
    return "Could not start recording for $forWhat: $why"
}

/** The source of the look at Android Auto's word that follows every poll. */
internal fun androidAutoRecheckText(believed: Boolean): String = if (believed) {
    "Android Auto, as last reported"
} else {
    "Android Auto has held the trip open alone for too long, taken for stuck"
}

/**
 * A reading of the truck taken at a process start, in words, with how it was reached.
 *
 * @param waitStands true if it could not be had and a stored wait beside the parked truck
 * carries on all the same (`storedWaitStandsUnread`).
 */
internal fun TruckReading.describe(waitStands: Boolean = false): String = when (answer) {
    TruckReading.Answer.CONNECTED -> "the truck is connected ($evidence)"

    TruckReading.Answer.NOT_CONNECTED -> "the truck is not connected ($evidence)"

    TruckReading.Answer.UNKNOWN -> {
        val unread = "the truck's connection could not be read ($evidence)"
        val then =
            if (waitStands) {
                "Dually was waiting beside the parked truck, and waits on until it can be read"
            } else {
                "so it counts as not connected"
            }
        "$unread, $then"
    }
}

/**
 * How a reading of the truck was reached, to follow what the trip rules were told: which
 * Bluetooth profile listed the truck, or why the phone could not say.
 */
internal fun TruckReading?.inWords(): String = when {
    this == null -> ""
    known -> " ($evidence)"
    else -> " (the truck's connection could not be read: $evidence)"
}

/**
 * The same for the reading a trigger took, which may have been set aside: a timer's reading of
 * "not connected" before any reading has shown the truck on its present link (amendment 18).
 */
internal fun Evidence.readingInWords(): String {
    if (!linkNotSeenYet) return reading.inWords()
    return " (it reads as not connected, ${reading?.evidence}, but no reading has shown it " +
        "connected since its link was made, so that proves nothing and what is believed stands)"
}

/**
 * The line for a trigger that told the trip rules nothing. Its reading of the truck could not
 * be had; or it said "not connected" before any reading had shown the truck connected on its
 * present link; or it is a poll's first "not connected", which is not yet believed.
 */
internal fun noNewsText(request: StartRequest, found: Evidence): String {
    val reading = found.reading
    val notConnected = "${request.source}: the truck reads as not connected${reading.inWords()}"
    return when {
        reading?.known == false ->
            "${request.source}: the truck's connection could not be read (${reading.evidence}). " +
                "Nothing changes"

        found.linkNotSeenYet ->
            "$notConnected. No reading has shown the truck connected since its link was made, " +
                "so this proves nothing"

        else -> "$notConnected. One reading alone proves nothing"
    }
}

/** Why a step that needs the trip service was not carried out: the service has to come up first. */
internal fun serviceNeededText(recording: Boolean): String = if (recording) {
    "Recording needs the trip service, which is not running"
} else {
    "Watching the parked truck needs the trip service, which is not running"
}

/** The line for a trip service that Android destroyed while it was needed. */
internal fun serviceLostText(recording: Boolean): String {
    val during = if (recording) "a trip is open" else "Dually is watching the parked truck"
    return "The trip service stopped while $during. Asking Android to start it again"
}

/** The line for what the controller holds in memory being dropped: nothing is doing the work. */
internal fun droppedText(recording: Boolean): String = if (recording) {
    "The open trip is not being recorded. The next trigger picks it up"
} else {
    "The parked truck is not being watched. The next trigger picks the wait up"
}
