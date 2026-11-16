package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.LocalDate
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.addDays
import com.shawnkowalchuk.milo.core.time.compareDates
import com.shawnkowalchuk.milo.core.time.compareTimes
import com.shawnkowalchuk.milo.core.time.lastDay
import com.shawnkowalchuk.milo.core.time.momentOf
import com.shawnkowalchuk.milo.core.time.monthsLater
import com.shawnkowalchuk.milo.core.time.timeOf
import com.shawnkowalchuk.milo.core.time.yearMonth
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.formatMonthName
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.report.monthSubmission
import com.shawnkowalchuk.milo.data.report.selectForReport
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import kotlinx.coroutines.flow.first

/** How many days ahead the daily check is planned: iOS keeps 64 notifications for an app. */
private const val DAILY_CHECK_DAYS_AHEAD = 7L

/**
 * The monthly reminder and the daily check on the iPhone (2026-11-15), by Android's rules
 * (`ReminderRules.kt` and `NothingRecordedRules.kt` in the app), as notifications planned ahead.
 *
 * Android decides each at the moment of asking, from an alarm once a day and whenever it runs.
 * An iPhone app cannot be woken at a set time, so the app plans the notifications that would be
 * shown from now on, as things stand, and plans them again whenever it runs: at its start, when
 * it comes to the front, when a trip starts or ends, when a report is recorded as sent, and when
 * a setting of theirs changes. What changes while the app is not running (a report sent from
 * another phone) is taken into account at its next run.
 */
class IosReminders internal constructor(
    private val settings: SettingsStore,
    private val trips: TripRepository,
    private val sent: SentReportRepository,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
) {
    /**
     * The monthly reminder: at 9:00 every day from the reminder day (or the month's last day in a
     * shorter month) to the end of this month, while last month has a Business trip and no
     * report for the whole of it is recorded as sent, and the reminder is switched on. Android
     * says it once a day in that span, from its 9:00 look; so does this.
     */
    suspend fun reminderPlan(nowMs: Long): ReminderPlan {
        val now = settings.current()
        val zoneNow = zone()
        val today = localDateOf(nowMs, zoneNow)
        val thisMonth = today.yearMonth()
        val lastMonth = thisMonth.monthsLater(-1)
        val name = formatMonthName(lastMonth, locale())
        val startsOn =
            today.addDays(
                (
                    minOf(
                        now.reminderDay,
                        thisMonth.lastDay().day,
                    ) - today.day
                    ).toLong(),
            )
        val span = monthSpan(lastMonth, zoneNow)
        val lastMonthTrips = trips.observeTripsStartedBetween(span.fromMs, span.untilMs).first()
        val waiting =
            now.reminderEnabled &&
                monthSubmission(lastMonth, sent.currentSent()) == null &&
                selectForReport(
                    lastMonthTrips,
                    ReportPeriod.Month(lastMonth),
                    zoneNow,
                ).trips.isNotEmpty()
        val fires =
            if (!waiting) {
                emptyList()
            } else {
                daysFrom(maxOf(startsOn, today), thisMonth.lastDay())
                    .map { momentOf(it, timeOf(9, 0), zoneNow, null) }
                    .filter { it > nowMs }
            }
        return ReminderPlan(
            fires = fires,
            title = "Mileage report for $name",
            text = "It has not been sent to your accountant yet. Tap to open it.",
            // Home's amber tile, as Android's, from the reminder day on.
            dueNow = waiting && compareDates(today, startsOn) >= 0,
            homeTitle = "$name report",
            year = lastMonth.year,
            month = lastMonth.monthNumber,
        )
    }

    /**
     * The daily check: on each work day of the next week, at the time set in Settings or the
     * start of that day's hours if later, unless a trip Dually opened was recorded that day
     * already (a trip added by hand does not count, nor one moved into the day on the edit
     * screen), and while the check is switched on. Today's is left out once it has been said.
     */
    suspend fun dailyCheckPlan(nowMs: Long): DailyCheckPlan {
        val now = settings.current()
        val check = now.nothingRecorded
        val zoneNow = zone()
        val today = localDateOf(nowMs, zoneNow)
        val fires =
            if (!check.enabled) {
                emptyList()
            } else {
                daysFrom(today, today.addDays(DAILY_CHECK_DAYS_AHEAD)).mapNotNull { day ->
                    val hours = now.schedule.on(day.dayOfWeek)
                    if (!hours.tracked) return@mapNotNull null
                    val from = if (compareTimes(hours.start, check.checkAt) >
                        0
                    ) {
                        hours.start
                    } else {
                        check.checkAt
                    }
                    val at = momentOf(day, from, zoneNow, null)
                    when {
                        at <= nowMs -> null
                        compareDates(day, today) == 0 && recordedOn(day) -> null
                        else -> at
                    }
                }
            }
        return DailyCheckPlan(
            fires = fires,
            title = "No trip recorded today",
            text =
                "That is fine if the truck has not been driven today. If it has, tap to open " +
                    "Dually and check that it is still set up.",
        )
    }

    /** Whether a trip Dually opened itself started on [day], as Android's `countsAsRecordedToday`. */
    private suspend fun recordedOn(day: LocalDate): Boolean {
        val span = daySpan(day, zone())
        return trips.observeTripsStartedBetween(
            span.fromMs - DAY_MS,
            span.untilMs,
        ).first().any { trip ->
            val start = trip.recordedStartedAtMs ?: trip.startedAtMs
            !trip.addedByHand &&
                (trip.status == TripStatus.OPEN || (start >= span.fromMs && start < span.untilMs))
        }
    }

    private fun daysFrom(first: LocalDate, last: LocalDate): List<LocalDate> {
        val days = mutableListOf<LocalDate>()
        var day = first
        while (compareDates(day, last) <= 0) {
            days += day
            day = day.addDays(1)
        }
        return days
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/**
 * The monthly reminder's notifications from now on, in epoch milliseconds, with their words;
 * [dueNow] is whether Home shows the amber tile for [year]/[month] ("October report"), whose
 * Send opens that report.
 */
data class ReminderPlan(
    val fires: List<Long>,
    val title: String,
    val text: String,
    val dueNow: Boolean,
    val homeTitle: String,
    val year: Int,
    val month: Int,
)

/** The daily check's notifications from now on, with their words. */
data class DailyCheckPlan(val fires: List<Long>, val title: String, val text: String)
