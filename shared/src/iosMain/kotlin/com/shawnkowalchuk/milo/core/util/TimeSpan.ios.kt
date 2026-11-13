package com.shawnkowalchuk.milo.core.util

import com.shawnkowalchuk.milo.core.time.LocalDate
import com.shawnkowalchuk.milo.core.time.YearMonth
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.compareDates
import com.shawnkowalchuk.milo.core.time.epochMsOf
import com.shawnkowalchuk.milo.core.time.firstDay
import com.shawnkowalchuk.milo.core.time.localDateTimeOf
import com.shawnkowalchuk.milo.core.time.yearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

// The iPhone's spans of days and months, worked out as the Android ones are (TimeSpan.kt):
// from local midnight, so a day on which the clocks change is as long as it really is.

// A midnight the clocks jump over starts the day when the jump ends, as java.time's atStartOfDay.
private fun LocalDate.startMs(zone: ZoneId): Long =
    epochMsOf(LocalDateTime(this, LocalTime(0, 0)), zone)

actual fun monthSpan(month: YearMonth, zone: ZoneId): TimeSpan = TimeSpan(
    fromMs = month.firstDay().startMs(zone),
    untilMs = month.firstDay().plus(1, DateTimeUnit.MONTH).startMs(zone),
)

actual fun daySpan(day: LocalDate, zone: ZoneId): TimeSpan = TimeSpan(
    fromMs = day.startMs(zone),
    untilMs = day.plus(1, DateTimeUnit.DAY).startMs(zone),
)

actual fun daysSpan(first: LocalDate, last: LocalDate, zone: ZoneId): TimeSpan {
    require(compareDates(last, first) >= 0) { "The days end ($last) before they start ($first)" }
    return TimeSpan(
        fromMs = first.startMs(zone),
        untilMs = last.plus(1, DateTimeUnit.DAY).startMs(zone),
    )
}

actual fun localDateOf(epochMs: Long, zone: ZoneId): LocalDate = localDateTimeOf(epochMs, zone).date

actual fun monthOf(epochMs: Long, zone: ZoneId): YearMonth = localDateOf(epochMs, zone).yearMonth()
