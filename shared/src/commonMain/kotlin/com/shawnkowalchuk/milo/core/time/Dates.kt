package com.shawnkowalchuk.milo.core.time

// The calendar the shared rules count with (ADR-014, phase 3). On Android each of these types
// is java.time's own (androidMain: `actual typealias`), so the app's code and its results are
// exactly what they were before the move. On the iPhone they are kotlinx-datetime's, or a small
// class of Dually's where that library has none. The shared code reaches them only through the
// functions below, whose Android bodies are the java.time calls the rules made before.

/** A day on the calendar, with no time and no zone. */
expect class LocalDate

/** A time on the clock, with no day and no zone. */
expect class LocalTime

/** A month of a year: November 2026. Its text is "2026-11". */
expect class YearMonth

/** A time zone: "America/Edmonton". Abstract, as java.time's is. */
expect abstract class ZoneId

/** The days of the week, Monday first. */
expect enum class DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
}

/** The months of the year. */
expect enum class Month {
    JANUARY,
    FEBRUARY,
    MARCH,
    APRIL,
    MAY,
    JUNE,
    JULY,
    AUGUST,
    SEPTEMBER,
    OCTOBER,
    NOVEMBER,
    DECEMBER,
}

/** The day [dayOfMonth] of [month] (1 to 12) of [year]. */
expect fun dateOf(year: Int, month: Int, dayOfMonth: Int): LocalDate

/** The year this day falls in. */
expect fun LocalDate.yearNumber(): Int

/** This day moved [days] forward (or back, if negative). */
expect fun LocalDate.addDays(days: Long): LocalDate

/** This day moved [months] forward, kept to the last day of a shorter month. */
expect fun LocalDate.addMonths(months: Long): LocalDate

/** This day moved [years] forward; 29 February becomes 28 February in a common year. */
expect fun LocalDate.addYears(years: Long): LocalDate

/** Whole days from this day to [other]: negative when [other] comes first. */
expect fun LocalDate.daysUntil(other: LocalDate): Long

/** Below zero when [a] comes before [b], zero on the same day, above zero after. */
expect fun compareDates(a: LocalDate, b: LocalDate): Int

/** The month's first day. */
expect fun YearMonth.firstDay(): LocalDate

/** The month's last day. */
expect fun YearMonth.lastDay(): LocalDate

/** [hour]:[minute] on the clock. */
expect fun timeOf(hour: Int, minute: Int): LocalTime

/** Seconds since midnight. */
expect fun LocalTime.secondsIntoDay(): Int

/** True when this time has no seconds and no fraction of one. */
expect fun LocalTime.isWholeMinute(): Boolean

/** Below zero when [a] is earlier in the day than [b], zero when equal, above zero later. */
expect fun compareTimes(a: LocalTime, b: LocalTime): Int

/** The day of the moment [epochMs] in [zone], as "2026-11-13", in any language. */
expect fun formatIsoDay(epochMs: Long, zone: ZoneId): String

/** The time of the moment [epochMs] in [zone], as "07:05" on a 24-hour clock, in any language. */
expect fun formatIsoTime(epochMs: Long, zone: ZoneId): String

/**
 * The day [epochDay] days after 1 January 1970, as stored in the database. A number beyond the
 * calendar's reach (which nothing in Dually writes) gives its first or last day.
 */
expect fun dateOfEpochDay(epochDay: Long): LocalDate

/** Days from 1 January 1970 to this day, as the database stores a day. */
expect fun LocalDate.epochDay(): Long

/** The month this day falls in. */
expect fun LocalDate.yearMonth(): YearMonth
