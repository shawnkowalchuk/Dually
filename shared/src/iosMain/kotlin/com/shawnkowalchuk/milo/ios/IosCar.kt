package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.allowance.allowanceCents
import com.shawnkowalchuk.milo.core.allowance.formatWholeDollars
import com.shawnkowalchuk.milo.core.io.IOException
import com.shawnkowalchuk.milo.core.odometer.formatOdometer
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.categoryWhileRecorded
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.core.util.formatMonthName
import com.shawnkowalchuk.milo.core.util.formatShortMonthName
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.core.util.wholeHoursAndMinutes
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setRunningTripCategory
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.categoryTotals
import com.shawnkowalchuk.milo.data.trip.todayTrips
import com.shawnkowalchuk.milo.data.trip.vehicleOdometers
import com.shawnkowalchuk.milo.feature.car.englishCarFormat
import com.shawnkowalchuk.milo.feature.car.englishCarText
import kotlinx.coroutines.flow.first

// The CarPlay screen (phase 4f, 2026-11-14; ADR-014): Android Auto's screen on the truck's
// display, with its words and its rules (platform/car/ in the app). One screen, nothing to
// scroll: "This trip" (what is being recorded, what it will be saved as, how far and how long)
// and "Business" (today, the month, and the odometer or the month's dollars), then Start trip
// or End trip, and while a trip records the switch to Business or Personal.

class IosCar internal constructor(
    private val recorder: IosTripRecorder,
    private val settings: SettingsStore,
    private val trips: TripRepository,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
) {
    /** The screen as it is now. */
    suspend fun screen(): CarText {
        val nowMs = clock()
        val zone = zone()
        val locale = locale()
        val trip = recorder.now()
        val stored = readSettings()
        val unit = stored?.distanceUnit ?: DistanceUnit.KILOMETRES
        val category = trip?.let { open ->
            stored?.let {
                val chosen = it.runningTripCategory?.takeIf { c -> c.tripId == open.tripId }
                categoryWhileRecorded(open.startedAtMs, chosen?.category, it.schedule, zone)
            }
        }
        val status = englishCarText(
            when {
                trip != null && trip.waitingForTruck -> "car_status_waiting_for_truck"
                trip != null -> "car_status_recording"
                recorder.parkedWaiting() -> "car_status_parked_waiting"
                else -> "car_status_not_recording"
            },
        )
        val tripLine = if (trip == null) {
            status
        } else {
            val statusAndCategory = category?.let {
                joined(status, englishCarText(categoryWord(it)), locale)
            } ?: status
            val shown = distance(formatDistance(trip.metres, unit, locale), unit, locale)
            val since = wholeHoursAndMinutes(nowMs - trip.startedAtMs)
            val figures = if (since.hours == 0L) {
                englishCarFormat("car_trip_minutes", locale, shown, since.minutes)
            } else {
                englishCarFormat(
                    "car_trip_hours_minutes",
                    locale,
                    shown,
                    since.hours,
                    since.minutes,
                )
            }
            joined(statusAndCategory, figures, locale)
        }
        return CarText(
            title = englishCarText("app_name"),
            tripTitle = englishCarText("car_row_this_trip"),
            tripLine = tripLine,
            businessTitle = englishCarText("car_row_business"),
            businessLine = businessLine(stored, nowMs, zone, locale, unit),
            recording = trip != null,
            button = englishCarText(if (trip == null) "home_start_trip" else "home_end_trip"),
            switchButton = category?.let {
                englishCarText(
                    if (it == TripCategory.BUSINESS) "car_make_personal" else "car_make_business",
                )
            },
        )
    }

    /**
     * The switch beside End trip: the trip being recorded is saved as the other kind, as on
     * Android Auto (`setRunningTripCategory`), with a line in the Log. False if nothing is
     * recorded or the choice could not be stored.
     */
    suspend fun switchCategory(): Boolean {
        val trip = recorder.now() ?: return false
        val stored = readSettings() ?: return false
        val chosen = stored.runningTripCategory?.takeIf { it.tripId == trip.tripId }?.category
        val shown = categoryWhileRecorded(trip.startedAtMs, chosen, stored.schedule, zone())
            ?: return false
        val to = if (shown ==
            TripCategory.BUSINESS
        ) {
            TripCategory.PERSONAL
        } else {
            TripCategory.BUSINESS
        }
        return try {
            settings.setRunningTripCategory(trip.tripId, to)
            val name = englishCarText(categoryWord(to))
            val what = "Trip ${trip.tripId}: $name chosen on the CarPlay screen, for the trip " +
                "being recorded"
            eventLog.add(clock(), EventCategory.TRIP, what)
            true
        } catch (notStored: IOException) {
            val what = "The CarPlay screen could not store Business or Personal"
            eventLog.add(clock(), EventCategory.ERROR, what, notStored.stackTraceToString())
            false
        }
    }

    /**
     * "Today 17.9 km · November 412.3 km, $289", or with the odometer "Today 17.9 km · Nov
     * 412.3 km · Odo 102,684 km", as Android Auto writes it: with the odometer the month is
     * named short and the dollars are left out, so that the line stays one line.
     */
    private suspend fun businessLine(
        stored: MiloSettings?,
        nowMs: Long,
        zone: ZoneId,
        locale: Locale,
        unit: DistanceUnit,
    ): String {
        val unknown = englishCarText("car_business_unknown")
        stored ?: return unknown
        val month = monthOf(nowMs, zone)
        val span = monthSpan(month, zone)
        val day = daySpan(localDateOf(nowMs, zone), zone)
        val monthTrips = try {
            trips.observeTripsStartedBetween(span.fromMs, span.untilMs).first()
        } catch (failed: IOException) {
            val what = "The CarPlay screen could not read the month's trips"
            eventLog.add(clock(), EventCategory.ERROR, what, failed.stackTraceToString())
            return unknown
        }
        val startedToday = monthTrips.filter {
            it.startedAtMs >= day.fromMs &&
                it.startedAtMs < day.untilMs
        }
        val todayShown = formatTenths(todayTrips(startedToday).totals(unit).business.tenths, locale)
        val monthShown = formatTenths(categoryTotals(monthTrips, unit).business.tenths, locale)
        val today =
            englishCarFormat("car_business_today", locale, distance(todayShown, unit, locale))
        val finished = trips.observeFinishedTrips().first()
        val odometer = vehicleOdometers(stored, finished, nowMs, zone).firstOrNull()?.figure
        if (odometer != null) {
            val monthWords = englishCarFormat(
                "car_business_month",
                locale,
                formatShortMonthName(month, locale),
                distance(monthShown, unit, locale),
            )
            val odo = englishCarFormat(
                "car_odometer",
                locale,
                distance(formatOdometer(odometer.value, locale), odometer.unit, locale),
            )
            return joined(joined(today, monthWords, locale), odo, locale)
        }
        val dollars = formatWholeDollars(
            allowanceCents(
                categoryTotals(monthTrips, DistanceUnit.KILOMETRES).business.tenths,
                stored.homeWidgetCentsPerKm,
            ),
            locale,
        )
        val monthWords = englishCarFormat(
            "car_business_month_dollars",
            locale,
            formatMonthName(month, locale),
            distance(monthShown, unit, locale),
            dollars,
        )
        return joined(today, monthWords, locale)
    }

    private suspend fun readSettings(): MiloSettings? = try {
        settings.current()
    } catch (unreadable: IOException) {
        null
    }
}

private fun categoryWord(category: TripCategory) =
    if (category == TripCategory.BUSINESS) "trip_business" else "trip_personal"

private fun joined(first: String, second: String, locale: Locale) =
    englishCarFormat("car_joined", locale, first, second)

private fun distance(figure: String, unit: DistanceUnit, locale: Locale) = englishCarFormat(
    if (unit ==
        DistanceUnit.MILES
    ) {
        "distance_mi"
    } else {
        "distance_km"
    },
    locale,
    figure,
)

/**
 * The CarPlay screen, in English.
 *
 * @param recording whether a trip is open: the button ends it, and otherwise starts one.
 * @param switchButton "Make Personal" or "Make Business" while a trip records and what it will
 * be saved as is known; null otherwise.
 */
data class CarText(
    val title: String,
    val tripTitle: String,
    val tripLine: String,
    val businessTitle: String,
    val businessLine: String,
    val recording: Boolean,
    val button: String,
    val switchButton: String?,
)
