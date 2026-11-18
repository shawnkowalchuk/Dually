package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.compareDates
import com.shawnkowalchuk.milo.core.time.yearMonthOf
import com.shawnkowalchuk.milo.core.trip.labelChoices
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear
import com.shawnkowalchuk.milo.core.util.formatShortDay
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.core.util.formatTimeSpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.core.util.usesTwelveHourClock
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.ByHandMark
import com.shawnkowalchuk.milo.data.trip.TripCorrections
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.feature.trips.TripKind
import com.shawnkowalchuk.milo.feature.trips.TripLine
import com.shawnkowalchuk.milo.feature.trips.monthSummary
import com.shawnkowalchuk.milo.platform.address.OpenTripStart
import com.shawnkowalchuk.milo.platform.address.TripPlace
import kotlinx.coroutines.flow.first

/**
 * The Trips screen's door to the shared code on the iPhone (phase 4c). A month is worked out by
 * the same Kotlin as Android's Trips screen ([monthSummary]) and handed to Swift as text ready to
 * show. Marking a trip Business or Personal, deleting, restoring and counting it go through
 * [TripCorrections], as on Android, with the same lines in the event log. Where a trip went is
 * worded by the same rules as Android's (TripPlaceTexts and TripPlaceWords in the app).
 *
 * @param openTripStart where the trip in progress started, from the address lookup.
 */
class IosTrips internal constructor(
    private val trips: TripRepository,
    private val corrections: TripCorrections,
    private val eventLog: EventLogRepository,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
    private val clock: () -> Long,
    private val openTripStart: () -> OpenTripStart?,
    private val unit: () -> DistanceUnit,
) {
    /**
     * The trips started in [month] (1 to 12) of [year]. Deleted and discarded trips are listed
     * only with [showLeftOut], as Android's switch does. [liveTripId] and [liveMetres] are the
     * trip being recorded, whose row has no distance until it closes.
     */
    suspend fun month(
        year: Int,
        month: Int,
        showLeftOut: Boolean,
        liveTripId: Long?,
        liveMetres: Double?,
    ): MonthText {
        val zoneNow = zone()
        val localeNow = locale()
        val unit = unit()
        val shown = yearMonthOf(year, month)
        val span = monthSpan(shown, zoneNow)
        val rows = trips.observeTripsStartedBetween(span.fromMs, span.untilMs).first()
        val summary =
            monthSummary(
                trips = rows,
                zone = zoneNow,
                showLeftOut = showLeftOut,
                liveTripId = liveTripId,
                liveDistanceMetres = liveMetres,
                liveStart = openTripStart(),
                unit = unit,
            )
        val twentyFourHour = !usesTwelveHourClock(localeNow)
        val today = localDateOf(clock(), zoneNow)
        fun lineText(line: TripLine) = TripText(
            tripId = line.id,
            time =
                line.endedAtMs?.let { end ->
                    val (start, ending) =
                        formatTimeSpan(line.startedAtMs, end, zoneNow, localeNow, twentyFourHour)
                    "$start – $ending"
                } ?: formatTimeOfDay(line.startedAtMs, zoneNow, localeNow, twentyFourHour),
            route = routeText(line),
            distance = line.distanceMetres?.let { formatDistance(it, unit, localeNow) },
            category =
                when (line.category) {
                    TripCategory.BUSINESS -> "Business"
                    TripCategory.PERSONAL -> "Personal"
                    null -> null
                },
            note = noteText(line),
            label = line.label,
            canMarkBusiness = TripCategory.BUSINESS in line.markableAs,
            canMarkPersonal = TripCategory.PERSONAL in line.markableAs,
            correction = line.kind.correction?.name,
            correctionWords =
                when (line.kind) {
                    TripKind.COUNTED -> "Delete"
                    TripKind.DELETED -> "Restore"
                    TripKind.DISCARDED -> "Count it"
                    TripKind.IN_PROGRESS -> null
                },
            canLabel = line.kind == TripKind.COUNTED,
            canEdit = line.editable,
            unit = unitWord(unit),
        )
        return MonthText(
            title = formatMonthAndYear(shown, localeNow),
            business = formatTenths(summary.totals.business.tenths, localeNow),
            personal = formatTenths(summary.totals.personal.tenths, localeNow),
            tripCount = summary.tripCount,
            businessCount = summary.totals.business.count,
            personalCount = summary.totals.personal.count,
            hiddenLeftOut = summary.hiddenLeftOut,
            unit = unitWord(unit),
            inProgress = summary.inProgress?.let(::lineText),
            days =
                summary.days.map { day ->
                    DayText(
                        title =
                            if (compareDates(day.date, today) == 0) {
                                "Today · " + formatShortDay(day.date, localeNow)
                            } else {
                                formatShortDay(day.date, localeNow)
                            },
                        business = formatTenths(day.businessTenths, localeNow),
                        trips = day.trips.map(::lineText),
                    )
                },
        )
    }

    /** Marks a finished trip Business or Personal by hand. */
    suspend fun mark(tripId: Long, business: Boolean): Boolean =
        corrections.mark(tripId, if (business) TripCategory.BUSINESS else TripCategory.PERSONAL)

    /**
     * Deletes, restores or counts a trip: [correction] is the name a [TripText] carries
     * ("DELETE", "RESTORE", "COUNT").
     */
    suspend fun correct(tripId: Long, correction: String): Boolean {
        val chosen =
            com.shawnkowalchuk.milo.data.trip.TripCorrection.entries
                .firstOrNull { it.name == correction }
                ?: return false
        return corrections.apply(tripId, chosen, where = ON_THE_IPHONE)
    }

    /** The labels to choose from for a trip: the one used where it ended first, then the rest. */
    suspend fun labelChoicesFor(tripId: Long): List<String> {
        val trip = trips.findTrip(tripId) ?: return emptyList()
        val choices = labelChoices(trip.endLatitude, trip.endLongitude, trips.labelledTrips())
        return (listOfNotNull(choices.suggested) + choices.used).distinct()
    }

    /** Sets a trip's label, or clears it with an empty one. */
    suspend fun setLabel(tripId: Long, label: String): Boolean {
        val kept = label.trim().takeIf { it.isNotEmpty() }
        val saved = trips.setLabel(tripId, kept)
        val what =
            when {
                !saved -> "Trip $tripId: the label was not saved: the trip is not finished"
                kept == null -> "Trip $tripId: label removed on the iPhone"
                else -> "Trip $tripId: labelled \"$kept\" on the iPhone"
            }
        eventLog.add(clock(), EventCategory.TRIP, what)
        return saved
    }

    // A discarded trip has no route line: its addresses are never looked up, and its note says
    // why it is not counted. A deleted trip shows only what was stored before it was deleted.
    private fun routeText(line: TripLine): String? = when (line.kind) {
        TripKind.DISCARDED -> null

        TripKind.DELETED ->
            if (line.from == null && line.to == null) {
                null
            } else {
                fromToText(line.from ?: TripPlace.NotFound, line.to ?: TripPlace.NotFound)
            }

        TripKind.IN_PROGRESS -> startText(line.from)

        // A finished trip always carries both; "not found" is the honest reading of a missing one.
        TripKind.COUNTED ->
            fromToText(line.from ?: TripPlace.NotFound, line.to ?: TripPlace.NotFound)
    }

    private fun fromToText(from: TripPlace, to: TripPlace): String = when {
        from == TripPlace.LookingUp && to == TripPlace.LookingUp -> "Looking up the addresses…"

        from == TripPlace.NotFound && to == TripPlace.NotFound -> "No address found for this trip."

        from == TripPlace.LeftBlank && to == TripPlace.LeftBlank ->
            "No address entered for this trip."

        else -> "${placeText(from)} → ${placeText(to)}"
    }

    /** A trip in progress: only where it started can be known, and none before its first fix. */
    private fun startText(from: TripPlace?): String = when (from) {
        null -> "Where it started is not known yet."
        is TripPlace.Known -> "From ${from.address}"
        TripPlace.LookingUp -> "Looking up where it started…"
        TripPlace.NotFound, TripPlace.LeftBlank -> "No address found for where it started."
    }

    private fun noteText(line: TripLine): String? = when {
        line.kind == TripKind.DELETED -> "Deleted by you. Not counted."

        line.kind == TripKind.DISCARDED && line.ignored ->
            "Outside your work hours, which are set to be ignored. Not counted."

        line.kind == TripKind.DISCARDED -> "Too short to keep. Not counted."

        line.mark == ByHandMark.ADDED -> "Added by hand"

        line.mark == ByHandMark.EDITED -> "Edited by hand"

        line.ranPastSchedule -> "Ran past your work hours"

        else -> null
    }

    private fun placeText(place: TripPlace): String = when (place) {
        is TripPlace.Known -> place.address
        TripPlace.LookingUp -> "looking up the address…"
        TripPlace.NotFound -> "no address found"
        TripPlace.LeftBlank -> "no address entered"
    }

    private companion object {
        const val ON_THE_IPHONE = "on the iPhone's Trips screen"
    }
}

/** A month of trips as the iPhone's Trips screen shows it. */
data class MonthText(
    /** "November 2026". */
    val title: String,
    /** The month's Business kilometres, "1192.1". */
    val business: String,
    val personal: String,
    val tripCount: Int,
    /** How many of them are Business, and how many Personal. */
    val businessCount: Int,
    val personalCount: Int,
    /** How many deleted or discarded trips the switch would add to the list. */
    val hiddenLeftOut: Int,
    /** "km" or "mi", as Settings has it: every distance of the month is in it. */
    val unit: String,
    val inProgress: TripText?,
    val days: List<DayText>,
)

/** One day: "Today · Fri, Nov 13", its Business kilometres, and its trips, newest first. */
data class DayText(val title: String, val business: String, val trips: List<TripText>)

/** One trip's row and what can be done with it. */
data class TripText(
    val tripId: Long,
    /** "8:02 – 8:31 AM", or the start alone for a trip still being recorded. */
    val time: String,
    /**
     * "77 Commerce Way, Edmonton → 3104 97 St NW, Edmonton", "From 77 Commerce Way, Edmonton"
     * for a trip in progress, a sentence when neither end is known, or null for a trip with no
     * places to show.
     */
    val route: String?,
    val distance: String?,
    val category: String?,
    val note: String?,
    val label: String?,
    val canMarkBusiness: Boolean,
    val canMarkPersonal: Boolean,
    /** The correction offered ("DELETE", "RESTORE", "COUNT"), for [IosTrips.correct]. */
    val correction: String?,
    /** Its words on the button: "Delete", "Restore", "Count it". */
    val correctionWords: String?,
    val canLabel: Boolean,
    /** A finished trip: its times, addresses and distance can be changed by hand. */
    val canEdit: Boolean,
    /** "km" or "mi". */
    val unit: String,
)
