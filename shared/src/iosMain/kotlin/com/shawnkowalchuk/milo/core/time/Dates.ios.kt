package com.shawnkowalchuk.milo.core.time

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil as calendarDaysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.resetSystemTimeZone
import platform.Foundation.systemTimeZone
import platform.Foundation.timeZoneWithName

// On the iPhone the shared calendar is kotlinx-datetime's, with a month and a zone of Dually's
// own where the library's do not fit the shared declarations (ADR-014, phase 3). Each function
// gives what its java.time namesake gives on Android; the shared tests check both.
//
// The zone's rules are Apple's (NSTimeZone), the ones the iPhone's own clock follows, and never
// kotlinx-datetime's: on the iPhone that library reads time zone files of its own, which can lag
// behind Apple's. On 2026-11-13 it still had Edmonton falling back to UTC-7 on 2026-11-01, when
// Apple's data (2026c) keeps Alberta at UTC-6 (FINDINGS_LOG). kotlinx-datetime is used here only
// for the calendar, and with UTC, which needs no rules.

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

    /** How far the clock in this zone is from UTC at [epochMs], in seconds: -21600 is UTC-6. */
    internal abstract fun offsetSecondsAt(epochMs: Long): Int

    override fun equals(other: Any?): Boolean = other is ZoneId && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = id
}

/** A zone with Apple's rules: what the iPhone's clock shows is what the shared code works with. */
private class AppleZone(private val zone: NSTimeZone) : ZoneId() {
    override val id: String get() = zone.name

    override fun offsetSecondsAt(epochMs: Long): Int = zone
        .secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(epochMs / MS_PER_SECOND))
        .toInt()
}

private const val MS_PER_SECOND = 1000.0

/** The zone named [id], for the iPhone app: "America/Edmonton". */
fun zoneIdOf(id: String): ZoneId = AppleZone(
    NSTimeZone.timeZoneWithName(id) ?: throw IllegalArgumentException("No time zone $id"),
)

/** The zone the iPhone is set to now, read again each time: the user can change it. */
fun systemZoneId(): ZoneId {
    NSTimeZone.resetSystemTimeZone()
    return AppleZone(NSTimeZone.systemTimeZone)
}

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

actual fun LocalDate.yearMonth(): YearMonth = YearMonth(year, month.number)

/** 1 January of year -9999 and 31 December 9999: the calendar's reach on the iPhone here. */
private const val FIRST_EPOCH_DAY = -4_371_587L
private const val LAST_EPOCH_DAY = 2_932_896L

actual fun dateOfEpochDay(epochDay: Long): LocalDate =
    LocalDate.fromEpochDays(epochDay.coerceIn(FIRST_EPOCH_DAY, LAST_EPOCH_DAY))

actual fun LocalDate.epochDay(): Long = toEpochDays()

/** [epochMs] as the date and time on the clock in [zone]. */
internal fun localDateTimeOf(epochMs: Long, zone: ZoneId): LocalDateTime =
    Instant.fromEpochMilliseconds(epochMs + zone.offsetSecondsAt(epochMs) * MS_PER_SECOND_LONG)
        .toLocalDateTime(TimeZone.UTC)

/**
 * The moment the clock in [zone] shows [local], as java.time's `atZone` takes it on Android: a
 * time the clocks jump over (spring forward) is moved on by the jump, and a time that happens
 * twice (fall back) is the first of the two. Offsets are read a day either side, so a zone
 * that changed its clocks twice within two days would be read wrong; none does.
 */
internal fun epochMsOf(local: LocalDateTime, zone: ZoneId): Long {
    val wallMs = local.toInstant(TimeZone.UTC).toEpochMilliseconds()
    val before = zone.offsetSecondsAt(wallMs - DAY_MS)
    val after = zone.offsetSecondsAt(wallMs + DAY_MS)
    val moments =
        listOf(before, after)
            .distinct()
            .map { offset -> wallMs - offset * MS_PER_SECOND_LONG }
            .filter { moment ->
                wallMs - zone.offsetSecondsAt(moment) * MS_PER_SECOND_LONG == moment
            }
    return moments.minOrNull() ?: (wallMs - before * MS_PER_SECOND_LONG)
}

private const val MS_PER_SECOND_LONG = 1000L
private const val DAY_MS = 24L * 60L * 60L * MS_PER_SECOND_LONG

internal fun isoDay(date: LocalDate): String =
    date.year.toString().padStart(4, '0') + "-" + twoDigits(date.month.number) + "-" +
        twoDigits(date.day)

internal fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

private val MONTH_TEXT = Regex("""([+-]?\d{4,9})-(\d{2})""")

actual fun yearMonthOfText(text: String): YearMonth? {
    val match = MONTH_TEXT.matchEntire(text) ?: return null
    val month = match.groupValues[2].toInt()
    return if (month in 1..MONTHS) YearMonth(match.groupValues[1].toInt(), month) else null
}

actual fun dateOfEpochDayOrNull(epochDay: Long): LocalDate? =
    if (epochDay in FIRST_EPOCH_DAY..LAST_EPOCH_DAY) LocalDate.fromEpochDays(epochDay) else null

actual fun dateOfEpochDayExactly(epochDay: Long): LocalDate = LocalDate.fromEpochDays(epochDay)

actual fun parseDate(text: String): LocalDate = LocalDate.parse(text)

actual fun dateOfText(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (notADay: IllegalArgumentException) {
    null
}

actual fun timeOfSecondOfDay(seconds: Long): LocalTime = LocalTime.fromSecondOfDay(seconds.toInt())

actual fun LocalTime.hourOfDay(): Int = hour

actual fun LocalTime.minuteOfHour(): Int = minute

actual fun compareMonths(a: YearMonth, b: YearMonth): Int =
    compareValuesBy(a, b, { it.year }, { it.monthNumber })

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

actual fun formatOffsetMoment(epochMs: Long, zone: ZoneId): String {
    val at = localDateTimeOf(epochMs, zone)
    val time = twoDigits(at.hour) + ":" + twoDigits(at.minute) +
        if (at.second != 0) ":" + twoDigits(at.second) else ""
    val offsetSeconds = zone.offsetSecondsAt(epochMs)
    val offset =
        if (offsetSeconds == 0) {
            "Z"
        } else {
            val sign = if (offsetSeconds < 0) "-" else "+"
            val total = kotlin.math.abs(offsetSeconds)
            val seconds = total % SECONDS_PER_MINUTE
            sign + twoDigits(total / SECONDS_PER_HOUR) + ":" +
                twoDigits(total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE) +
                if (seconds != 0) ":" + twoDigits(seconds) else ""
        }
    return isoDay(at.date) + "T" + time + offset
}

actual fun formatTimeWithSeconds(epochMs: Long, zone: ZoneId): String {
    val time = localDateTimeOf(epochMs, zone)
    return "${twoDigits(time.hour)}:${twoDigits(time.minute)}:${twoDigits(time.second)}"
}

actual fun YearMonth.monthsLater(months: Long): YearMonth {
    val index = year.toLong() * MONTHS + (monthNumber - 1) + months
    return YearMonth(
        index.floorDiv(MONTHS.toLong()).toInt(),
        index.mod(MONTHS.toLong()).toInt() + 1,
    )
}

actual fun yearMonthOf(year: Int, month: Int): YearMonth = YearMonth(year, month)
