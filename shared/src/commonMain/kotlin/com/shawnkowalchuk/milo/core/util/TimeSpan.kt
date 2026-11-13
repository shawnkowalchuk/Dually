package com.shawnkowalchuk.milo.core.util

import com.shawnkowalchuk.milo.core.time.LocalDate
import com.shawnkowalchuk.milo.core.time.YearMonth
import com.shawnkowalchuk.milo.core.time.ZoneId

/**
 * A span of stored time: from [fromMs] up to, but not including, [untilMs]. Both are wall-clock
 * milliseconds since 1970, the form every stored time has.
 *
 * Half-open on purpose. The end of one month is the start of the next, so a trip that starts on
 * the stroke of midnight belongs to exactly one of them.
 */
data class TimeSpan(val fromMs: Long, val untilMs: Long)

/**
 * The span of time a calendar month covers in [zone]: from the first instant of its first day to
 * the first instant of the next month.
 *
 * The month is worked out in a time zone because "October" is not a fixed span of milliseconds:
 * it starts at local midnight, and a month in which the clocks change is an hour longer or
 * shorter than its days suggest. `atStartOfDay` also copes with a zone whose clocks jump over
 * midnight itself.
 */
expect fun monthSpan(month: YearMonth, zone: ZoneId): TimeSpan

/**
 * The span of time a calendar day covers in [zone]: from local midnight to the next local
 * midnight. A day on which the clocks change is an hour shorter or longer than 24 hours, which
 * is why the end is worked out from the next day and not by adding 24 hours.
 */
expect fun daySpan(day: LocalDate, zone: ZoneId): TimeSpan

/**
 * The span of time a run of calendar days covers in [zone]: from local midnight of [first] to
 * the local midnight that ends [last]. Both days are inside the span, which is how a date range
 * is read on a report: "5 to 18 October" includes the 18th.
 *
 * Worked out from the day after [last] and not by counting hours, like [daySpan], so a run of
 * days in which the clocks change is as long as it really was.
 */
expect fun daysSpan(first: LocalDate, last: LocalDate, zone: ZoneId): TimeSpan

/** The calendar day a stored time falls on in [zone]. */
expect fun localDateOf(epochMs: Long, zone: ZoneId): LocalDate

/** The calendar month a stored time falls in, in [zone]. */
expect fun monthOf(epochMs: Long, zone: ZoneId): YearMonth
