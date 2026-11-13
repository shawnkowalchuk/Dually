package com.shawnkowalchuk.milo.core.time

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil as calendarDaysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

// On the iPhone the shared calendar is kotlinx-datetime's, with a month and a zone of Dually's
// own where the library's do not fit the shared declarations (ADR-014, phase 3). Each function
// gives what its java.time namesake gives on Android; the shared tests check both.

actual typealias LocalDate = kotlinx.datetime.LocalDate

actual typealias LocalTime = kotlinx.datetime.LocalTime

actual typealias DayOfWeek = kotlinx.datetime.DayOfWeek

actual typealias Month = kotlinx.datetime.Month

/** A month of a year. Written "2026-11", as java.time writes it. */
actual class YearMonth(val year: Int, val monthNumber: Int) {
    init {
        require(monthNumber in 1..MONTHS) { "A month is 1 to 12, but was $monthNumber" }
    }

    override fun equals(other: Any?): Boolean =
        other is YearMonth && other.year == year && other.monthNumber == monthNumber

    override fun hashCode(): Int = year * MONTHS + monthNumber

    override fun toString(): String = year.toString().padStart(4, '0') + "-" +
        monthNumber.toString().padStart(2, '0')
}

private const val MONTHS = 12

/** A time zone. Equal to another with the same id, as java.time's are. */
actual abstract class ZoneId internal constructor() {
    /** The zone's id: "America/Edmonton". */
    abstract val id: String

    internal abstract val timeZone: TimeZone

    override fun equals(other: Any?): Boolean = other is ZoneId && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = id
}

private class KotlinxZone(override val timeZone: TimeZone) : ZoneId() {
    override val id: String get() = timeZone.id
}

/** The zone named [id], for the iPhone app: "America/Edmonton". */
fun zoneIdOf(id: String): ZoneId = KotlinxZone(TimeZone.of(id))

/** The zone the iPhone is set to now. */
fun systemZoneId(): ZoneId = KotlinxZone(TimeZone.currentSystemDefault())

actual fun dateOf(year: Int, month: Int, dayOfMonth: Int): LocalDate =
    LocalDate(year, month, dayOfMonth)

actual fun LocalDate.yearNumber(): Int = year

actual fun LocalDate.addDays(days: Long): LocalDate = plus(days, DateTimeUnit.DAY)

actual fun LocalDate.addMonths(months: Long): LocalDate = plus(months, DateTimeUnit.MONTH)

actual fun LocalDate.addYears(years: Long): LocalDate = plus(years, DateTimeUnit.YEAR)

actual fun LocalDate.daysUntil(other: LocalDate): Long = calendarDaysUntil(other).toLong()

actual fun compareDates(a: LocalDate, b: LocalDate): Int = a.compareTo(b)

actual fun YearMonth.firstDay(): LocalDate = LocalDate(year, monthNumber, 1)

actual fun YearMonth.lastDay(): LocalDate =
    firstDay().plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

actual fun timeOf(hour: Int, minute: Int): LocalTime = LocalTime(hour, minute)

actual fun LocalTime.secondsIntoDay(): Int = toSecondOfDay()

actual fun LocalTime.isWholeMinute(): Boolean = second == 0 && nanosecond == 0

actual fun compareTimes(a: LocalTime, b: LocalTime): Int = a.compareTo(b)

actual fun formatIsoDay(epochMs: Long, zone: ZoneId): String =
    isoDay(localDateTimeOf(epochMs, zone).date)

actual fun formatIsoTime(epochMs: Long, zone: ZoneId): String {
    val time = localDateTimeOf(epochMs, zone).time
    return twoDigits(time.hour) + ":" + twoDigits(time.minute)
}

/** The month a day falls in. */
fun LocalDate.yearMonth(): YearMonth = YearMonth(year, month.number)

internal fun localDateTimeOf(epochMs: Long, zone: ZoneId) =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone.timeZone)

internal fun isoDay(date: LocalDate): String =
    date.year.toString().padStart(4, '0') + "-" + twoDigits(date.month.number) + "-" +
        twoDigits(date.day)

internal fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
