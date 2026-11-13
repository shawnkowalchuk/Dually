package com.shawnkowalchuk.milo.core.time

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// On Android the shared calendar is java.time's own: the same objects and the same calls the
// rules made before they moved to :shared (ADR-014, phase 3).

actual typealias LocalDate = java.time.LocalDate

actual typealias LocalTime = java.time.LocalTime

actual typealias YearMonth = java.time.YearMonth

actual typealias ZoneId = java.time.ZoneId

actual typealias DayOfWeek = java.time.DayOfWeek

actual typealias Month = java.time.Month

actual fun dateOf(year: Int, month: Int, dayOfMonth: Int): LocalDate =
    LocalDate.of(year, month, dayOfMonth)

actual fun LocalDate.yearNumber(): Int = year

actual fun LocalDate.addDays(days: Long): LocalDate = plusDays(days)

actual fun LocalDate.addMonths(months: Long): LocalDate = plusMonths(months)

actual fun LocalDate.addYears(years: Long): LocalDate = plusYears(years)

actual fun LocalDate.daysUntil(other: LocalDate): Long = ChronoUnit.DAYS.between(this, other)

actual fun compareDates(a: LocalDate, b: LocalDate): Int = a.compareTo(b)

actual fun YearMonth.firstDay(): LocalDate = atDay(1)

actual fun YearMonth.lastDay(): LocalDate = atEndOfMonth()

actual fun timeOf(hour: Int, minute: Int): LocalTime = LocalTime.of(hour, minute)

actual fun LocalTime.secondsIntoDay(): Int = toSecondOfDay()

actual fun LocalTime.isWholeMinute(): Boolean = this == truncatedTo(ChronoUnit.MINUTES)

actual fun compareTimes(a: LocalTime, b: LocalTime): Int = a.compareTo(b)

private val ISO_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "yyyy-MM-dd",
    java.util.Locale.ROOT,
)
private val ISO_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "HH:mm",
    java.util.Locale.ROOT,
)

actual fun formatIsoDay(epochMs: Long, zone: ZoneId): String =
    ISO_DAY.format(Instant.ofEpochMilli(epochMs).atZone(zone))

actual fun formatIsoTime(epochMs: Long, zone: ZoneId): String =
    ISO_TIME.format(Instant.ofEpochMilli(epochMs).atZone(zone))

actual fun dateOfEpochDay(epochDay: Long): LocalDate =
    LocalDate.ofEpochDay(epochDay.coerceIn(LocalDate.MIN.toEpochDay(), LocalDate.MAX.toEpochDay()))

actual fun LocalDate.epochDay(): Long = toEpochDay()

actual fun LocalDate.yearMonth(): YearMonth = YearMonth.from(this)

actual fun yearMonthOfText(text: String): YearMonth? = try {
    YearMonth.parse(text)
} catch (notAMonth: java.time.format.DateTimeParseException) {
    null
}

actual fun dateOfEpochDayOrNull(epochDay: Long): LocalDate? = epochDay
    .takeIf { it in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay() }
    ?.let(LocalDate::ofEpochDay)

actual fun dateOfEpochDayExactly(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

actual fun parseDate(text: String): LocalDate = LocalDate.parse(text)

actual fun dateOfText(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (notADay: java.time.DateTimeException) {
    null
}

actual fun timeOfSecondOfDay(seconds: Long): LocalTime = LocalTime.ofSecondOfDay(seconds)

actual fun LocalTime.hourOfDay(): Int = hour

actual fun LocalTime.minuteOfHour(): Int = minute

actual fun compareMonths(a: YearMonth, b: YearMonth): Int = a.compareTo(b)

actual fun formatOffsetMoment(epochMs: Long, zone: ZoneId): String = Instant.ofEpochMilli(epochMs)
    .atZone(zone)
    .toOffsetDateTime()
    .truncatedTo(ChronoUnit.SECONDS)
    .toString()

private val TIME_WITH_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

actual fun formatTimeWithSeconds(epochMs: Long, zone: ZoneId): String =
    TIME_WITH_SECONDS.format(Instant.ofEpochMilli(epochMs).atZone(zone))

actual fun YearMonth.monthsLater(months: Long): YearMonth = plusMonths(months)

actual fun yearMonthOf(year: Int, month: Int): YearMonth = YearMonth.of(year, month)
