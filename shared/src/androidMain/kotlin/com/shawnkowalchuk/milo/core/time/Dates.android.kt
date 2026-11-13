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
