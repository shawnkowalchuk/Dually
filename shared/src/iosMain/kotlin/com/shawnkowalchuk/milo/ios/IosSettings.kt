package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.DayOfWeek
import com.shawnkowalchuk.milo.core.time.secondsIntoDay
import com.shawnkowalchuk.milo.core.time.timeOfSecondOfDay
import com.shawnkowalchuk.milo.core.trip.MAX_ADJUST_STEPS
import com.shawnkowalchuk.milo.core.trip.shownAdjustPercent
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatClockTime
import com.shawnkowalchuk.milo.core.util.formatShortDayOfWeek
import com.shawnkowalchuk.milo.core.util.formatShortDistance
import com.shawnkowalchuk.milo.core.util.usesTwelveHourClock
import com.shawnkowalchuk.milo.data.settings.GRACE_PERIOD_CHOICE
import com.shawnkowalchuk.milo.data.settings.MINIMUM_TRIP_DISTANCE_CHOICE
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.PARKED_LIMIT_CHOICE
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setDistanceAdjustSteps
import com.shawnkowalchuk.milo.data.settings.setDistanceUnit
import com.shawnkowalchuk.milo.data.settings.setParkedLimitSeconds

/**
 * The iPhone's Settings screen (phase 4g, first part) through the shared settings store: the
 * same file, keys, ranges and steps as Android's Settings screen (SettingsViewModel in the app),
 * worded for Swift. A change is stored at once, as on Android, and applies from the next trip.
 */
class IosSettings internal constructor(
    private val settings: SettingsStore,
    private val locale: () -> Locale,
) {
    /** Everything the screen shows, as of now. */
    suspend fun read(): SettingsText = text(settings.current())

    suspend fun stepGrace(longer: Boolean) = change {
        settings.setGracePeriodSeconds(GRACE_PERIOD_CHOICE.next(it.gracePeriodSeconds, longer))
    }

    suspend fun stepParkedLimit(longer: Boolean) = change {
        settings.setParkedLimitSeconds(PARKED_LIMIT_CHOICE.next(it.parkedLimitSeconds, longer))
    }

    suspend fun stepShortestTrip(longer: Boolean) = change {
        settings.setMinimumTripDistanceMetres(
            MINIMUM_TRIP_DISTANCE_CHOICE.next(it.minimumTripDistanceMetres, longer),
        )
    }

    /** The distance adjustment, half a percent up or down (ADR-011). */
    suspend fun stepAdjustment(up: Boolean) = change {
        val steps = it.distanceAdjustSteps + if (up) 1 else -1
        settings.setDistanceAdjustSteps(steps.coerceIn(-MAX_ADJUST_STEPS, MAX_ADJUST_STEPS))
    }

    suspend fun setMiles(miles: Boolean) = change {
        settings.setDistanceUnit(if (miles) DistanceUnit.MILES else DistanceUnit.KILOMETRES)
    }

    suspend fun setDrivingAlert(on: Boolean) = change { settings.setDrivingAlertEnabled(on) }

    /** Trips outside the work hours: saved as Personal (false) or ignored (true). */
    suspend fun setIgnoreOutsideHours(ignore: Boolean) = change {
        settings.setIgnoreTripsOutsideSchedule(ignore)
    }

    /** [dayIndex] 0 is Monday, as [SettingsText.days] lists them. */
    suspend fun setWorkDay(dayIndex: Int, tracked: Boolean) = change {
        settings.setSchedule(it.schedule.withTracked(DayOfWeek.entries[dayIndex], tracked))
    }

    /**
     * The start and end of every day, in minutes from midnight, as Android's slider for the
     * whole week sets them: the days that are off too, so a day switched on later has them.
     * Returns false, storing nothing, if the end is not after the start.
     */
    suspend fun setHours(startMinute: Int, endMinute: Int): Boolean {
        if (endMinute <= startMinute) return false
        val start = timeOfSecondOfDay(startMinute * SECONDS_PER_MINUTE.toLong())
        val end = timeOfSecondOfDay(endMinute * SECONDS_PER_MINUTE.toLong())
        var stored = false
        change { now ->
            val changed =
                DayOfWeek.entries.fold<DayOfWeek, WorkSchedule?>(now.schedule) { schedule, day ->
                    schedule?.let { it.with(day, it.on(day).copy(start = start, end = end)) }
                }
            if (changed != null) {
                settings.setSchedule(changed)
                stored = true
            }
        }
        return stored
    }

    private suspend fun change(work: suspend (MiloSettings) -> Unit): SettingsText {
        work(settings.current())
        return read()
    }

    private fun text(now: MiloSettings): SettingsText {
        val localeNow = locale()
        val twentyFour = !usesTwelveHourClock(localeNow)
        val unit = now.distanceUnit
        val schedule = now.schedule
        val tracked = DayOfWeek.entries.filter { schedule.on(it).tracked }
        val hours = tracked.map {
            schedule.on(it)
        }.ifEmpty { listOf(schedule.on(DayOfWeek.MONDAY)) }
        val first = hours.first()
        val sameHours = hours.all { it.start == first.start && it.end == first.end }
        val weekMinutes =
            tracked.sumOf {
                val day = schedule.on(it)
                (day.end.secondsIntoDay() - day.start.secondsIntoDay()) / SECONDS_PER_MINUTE
            }
        return SettingsText(
            miles = unit == DistanceUnit.MILES,
            drivingAlert = now.drivingAlertEnabled,
            days =
                DayOfWeek.entries.map {
                    WorkDayText(formatShortDayOfWeek(it, localeNow), schedule.on(it).tracked)
                },
            startMinute = first.start.secondsIntoDay() / SECONDS_PER_MINUTE,
            endMinute = first.end.secondsIntoDay() / SECONDS_PER_MINUTE,
            hours =
                formatClockTime(first.start, localeNow, twentyFour) + " – " +
                    formatClockTime(first.end, localeNow, twentyFour),
            sameHoursEveryDay = sameHours,
            week =
                when (tracked.size) {
                    0 -> "No days tracked: every trip is outside the work hours"

                    else -> "${tracked.size} ${if (tracked.size == 1) "day" else "days"} · " +
                        hoursText(weekMinutes) + " a week"
                },
            ignoreOutsideHours = now.ignoreTripsOutsideSchedule,
            grace = minutesText(now.gracePeriodSeconds),
            graceLonger = GRACE_PERIOD_CHOICE.canStepUp(now.gracePeriodSeconds),
            graceShorter = GRACE_PERIOD_CHOICE.canStepDown(now.gracePeriodSeconds),
            parkedLimit = minutesText(now.parkedLimitSeconds),
            parkedLonger = PARKED_LIMIT_CHOICE.canStepUp(now.parkedLimitSeconds),
            parkedShorter = PARKED_LIMIT_CHOICE.canStepDown(now.parkedLimitSeconds),
            shortestTrip =
                formatShortDistance(now.minimumTripDistanceMetres.toDouble(), unit, localeNow) +
                    " " + unitWord(unit),
            shortestLonger = MINIMUM_TRIP_DISTANCE_CHOICE.canStepUp(now.minimumTripDistanceMetres),
            shortestShorter =
                MINIMUM_TRIP_DISTANCE_CHOICE.canStepDown(now.minimumTripDistanceMetres),
            adjustment = adjustText(now.distanceAdjustSteps),
            adjustUp = now.distanceAdjustSteps < MAX_ADJUST_STEPS,
            adjustDown = now.distanceAdjustSteps > -MAX_ADJUST_STEPS,
        )
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val MINUTES_PER_HOUR = 60
    }

    /** "2 min", "2.5 min", as Android's Trips tile writes them. */
    private fun minutesText(seconds: Int): String {
        val whole = seconds / SECONDS_PER_MINUTE
        return if (seconds % SECONDS_PER_MINUTE == 0) "$whole min" else "$whole.5 min"
    }

    /** "42.5 h", or "40 h". */
    private fun hoursText(minutes: Int): String {
        val tenths = (minutes * 10 + MINUTES_PER_HOUR / 2) / MINUTES_PER_HOUR
        return if (tenths % 10 == 0) "${tenths / 10} h" else "${tenths / 10}.${tenths % 10} h"
    }

    /** "0 %", "+1.5 %", "−2 %", as Android's. */
    private fun adjustText(steps: Int): String {
        val percent = shownAdjustPercent(steps)
        val magnitude = kotlin.math.abs(percent)
        val number =
            if (magnitude % 1.0 == 0.0) magnitude.toInt().toString() else magnitude.toString()
        return when {
            percent > 0 -> "+$number %"
            percent < 0 -> "−$number %"
            else -> "0 %"
        }
    }
}

/** The word Dually writes after a distance: "km" or "mi". */
fun unitWord(unit: DistanceUnit): String = if (unit == DistanceUnit.MILES) "mi" else "km"

private fun com.shawnkowalchuk.milo.data.settings.SteppedChoice.next(value: Int, up: Boolean) =
    if (up) stepUp(value) else stepDown(value)

/** One day of the work schedule, as its button says it. */
data class WorkDayText(val name: String, val tracked: Boolean)

/** The Settings screen's values, worded, and which steps are left. */
data class SettingsText(
    val miles: Boolean,
    val drivingAlert: Boolean,
    /** Monday first. */
    val days: List<WorkDayText>,
    /** The hours, in minutes from midnight, of the first work day: the week's when they agree. */
    val startMinute: Int,
    val endMinute: Int,
    /** "8:00 AM – 4:30 PM". */
    val hours: String,
    /** False when work days have hours of their own, which only Android's screen sets yet. */
    val sameHoursEveryDay: Boolean,
    /** "5 days · 42.5 h a week". */
    val week: String,
    val ignoreOutsideHours: Boolean,
    val grace: String,
    val graceLonger: Boolean,
    val graceShorter: Boolean,
    val parkedLimit: String,
    val parkedLonger: Boolean,
    val parkedShorter: Boolean,
    val shortestTrip: String,
    val shortestLonger: Boolean,
    val shortestShorter: Boolean,
    val adjustment: String,
    val adjustUp: Boolean,
    val adjustDown: Boolean,
)
