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
import com.shawnkowalchuk.milo.platform.address.TripPlace
import kotlinx.coroutines.flow.first

/**
 * The Trips screen's door to the shared code on the iPhone (phase 4c). A month is worked out by
 * the same Kotlin as Android's Trips screen ([monthSummary]) and handed to Swift as text ready to
 * show. Marking a trip Business or Personal, deleting, restoring and counting it go through
 * [TripCorrections], as on Android, with the same lines in the event log.
 */
class IosTrips internal constructor(
    private val trips: TripRepository,
    private val corrections: TripCorrections,
    private val eventLog: EventLogRepository,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
    private val clock: () -> Long,
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
        val unit = DistanceUnit.KILOMETRES
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
                liveStart = null,
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
        )
        return MonthText(
            title = formatMonthAndYear(shown, localeNow),
            business = formatTenths(summary.totals.business.tenths, localeNow),
            personal = formatTenths(summary.totals.personal.tenths, localeNow),
            tripCount = summary.tripCount,
            businessCount = summary.totals.business.count,
            personalCount = summary.totals.personal.count,
            hiddenLeftOut = summary.hiddenLeftOut,
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

    private fun routeText(line: TripLine): String? = when {
        line.kind == TripKind.IN_PROGRESS || line.kind == TripKind.DISCARDED -> null

        // A deleted trip shows only the places it had: none at all is no line at all.
        line.from == null && line.to == null -> null

        line.from == TripPlace.LookingUp && line.to == TripPlace.LookingUp ->
            "Addresses not looked up yet"

        else -> "${placeText(line.from)} → ${placeText(line.to)}"
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

    // On the iPhone no address is looked up yet: that comes in a later step. A trip without
    // one says so, rather than Android's "looking up the address…".
    private fun placeText(place: TripPlace?): String = when (place) {
        is TripPlace.Known -> place.address
        TripPlace.LookingUp -> "address not looked up yet"
        TripPlace.LeftBlank -> "no address entered"
        TripPlace.NotFound, null -> "no address found"
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
    /** "77 Commerce Way → 3300 Gateway Blvd", or null for a trip with no places to show. */
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
)
