package com.shawnkowalchuk.milo.core.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * A plain 24-hour time, for a phone that is set to 24 hours in a language that writes AM and PM.
 * It is how Android itself writes the time on such a phone.
 */
private const val TWENTY_FOUR_HOUR_PATTERN = "HH:mm"

/** And the other way round: a phone set to AM and PM in a language that writes 24 hours. */
private const val TWELVE_HOUR_PATTERN = "h:mm a"

actual fun formatTimeOfDay(
    epochMs: Long,
    zone: ZoneId,
    locale: Locale,
    twentyFourHour: Boolean,
): String =
    timeOfDayFormat(locale, twentyFourHour).withZone(zone).format(Instant.ofEpochMilli(epochMs))

actual fun formatClockTime(time: LocalTime, locale: Locale, twentyFourHour: Boolean): String =
    timeOfDayFormat(locale, twentyFourHour).format(time)

/**
 * The language's own short form wherever it already has the clock the phone is set to, as it
 * does on a phone whose 24-hour switch follows the language. Only where the two disagree is a
 * plain pattern used in its place.
 */
private fun timeOfDayFormat(locale: Locale, twentyFourHour: Boolean): DateTimeFormatter = when {
    usesTwelveHourClock(locale) != twentyFourHour ->
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    twentyFourHour -> DateTimeFormatter.ofPattern(TWENTY_FOUR_HOUR_PATTERN, locale)

    else -> DateTimeFormatter.ofPattern(TWELVE_HOUR_PATTERN, locale)
}

internal actual fun usesTwelveHourClock(locale: Locale): Boolean {
    val pattern =
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            null,
            FormatStyle.SHORT,
            IsoChronology.INSTANCE,
            locale,
        )
    // In a date and time pattern "h" and "K" are the hours that run to 12, "H" and "k" the ones
    // that run to 24. The hour letter is asked, not the AM or PM marker: some languages mark
    // the half of the day with another letter. Text between single quotes is written out as it
    // is (the "h" of French "8 h 14") and is left aside.
    return pattern.split('\'').filterIndexed { index, _ -> index % 2 == 0 }.any { part ->
        part.any { it == 'h' || it == 'K' }
    }
}

actual fun formatDayOfWeek(day: DayOfWeek, locale: Locale): String =
    day.getDisplayName(TextStyle.FULL, locale)

/** The event log's time format: fixed, sortable, and exact to the second. */
private val LOG_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

actual fun formatLogTime(epochMs: Long, zone: ZoneId): String =
    LOG_TIME.withZone(zone).format(Instant.ofEpochMilli(epochMs))

actual fun formatMonthAndYear(month: YearMonth, locale: Locale): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month)

actual fun formatMonthName(month: YearMonth, locale: Locale): String =
    DateTimeFormatter.ofPattern("LLLL", locale).format(month)

actual fun formatShortDay(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern("EEE, MMM d", locale).format(date)

actual fun formatDay(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)

actual fun formatMediumDay(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

actual fun formatDate(epochMs: Long, zone: ZoneId, locale: Locale): String = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(locale)
    .withZone(zone)
    .format(Instant.ofEpochMilli(epochMs))

actual fun formatLongDay(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date)

actual fun formatFullMonthName(month: YearMonth, locale: Locale): String =
    month.month.getDisplayName(TextStyle.FULL, locale)

actual fun formatShortMonthName(month: YearMonth, locale: Locale): String =
    DateTimeFormatter.ofPattern("MMM", locale).format(month)
