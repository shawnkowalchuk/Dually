package com.shawnkowalchuk.milo.core.util

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.DayOfWeek
import com.shawnkowalchuk.milo.core.time.LocalDate
import com.shawnkowalchuk.milo.core.time.LocalTime
import com.shawnkowalchuk.milo.core.time.YearMonth
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.firstDay
import com.shawnkowalchuk.milo.core.time.isoDay
import com.shawnkowalchuk.milo.core.time.localDateTimeOf
import com.shawnkowalchuk.milo.core.time.twoDigits
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterStyle
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName

// The iPhone's dates and times in words, by Apple's formatter, with the same patterns the
// Android side gives java.time (TimeFormat.kt). Where a form is the language's own ("FULL",
// "MEDIUM"), Apple's wording of it is used: that is how the iPhone writes dates everywhere else.

private const val TWENTY_FOUR_HOUR_PATTERN = "HH:mm"
private const val TWELVE_HOUR_PATTERN = "h:mm a"
private const val MS_PER_SECOND = 1000.0
private const val DAYS_IN_A_WEEK = 7

private fun moment(epochMs: Long): NSDate =
    NSDate.dateWithTimeIntervalSince1970(epochMs / MS_PER_SECOND)

/** Noon of [date], in UTC: a moment that is on that day whatever the clocks did. */
private fun noonOf(date: LocalDate): NSDate =
    moment(LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.UTC).toEpochMilliseconds())

private fun formatter(locale: Locale, zone: String = "UTC"): NSDateFormatter =
    NSDateFormatter().apply {
        this.locale = locale.nsLocale
        timeZone = NSTimeZone.timeZoneWithName(zone) ?: error("No time zone $zone")
    }

private fun styled(locale: Locale, style: NSDateFormatterStyle, zone: String = "UTC") =
    formatter(locale, zone).apply {
        dateStyle = style
        timeStyle = NSDateFormatterNoStyle
    }

private fun patterned(locale: Locale, pattern: String, zone: String = "UTC") =
    formatter(locale, zone).apply { dateFormat = pattern }

private fun timeOfDayFormat(locale: Locale, twentyFourHour: Boolean, zone: String) = when {
    usesTwelveHourClock(locale) != twentyFourHour ->
        formatter(locale, zone).apply {
            dateStyle = NSDateFormatterNoStyle
            timeStyle = NSDateFormatterShortStyle
        }

    twentyFourHour -> patterned(locale, TWENTY_FOUR_HOUR_PATTERN, zone)

    else -> patterned(locale, TWELVE_HOUR_PATTERN, zone)
}

actual fun formatTimeOfDay(
    epochMs: Long,
    zone: ZoneId,
    locale: Locale,
    twentyFourHour: Boolean,
): String = timeOfDayFormat(locale, twentyFourHour, zone.id).stringFromDate(moment(epochMs))

actual fun formatClockTime(time: LocalTime, locale: Locale, twentyFourHour: Boolean): String =
    timeOfDayFormat(locale, twentyFourHour, "UTC").stringFromDate(
        moment(time.toSecondOfDay() * MS_PER_SECOND.toLong()),
    )

internal actual fun usesTwelveHourClock(locale: Locale): Boolean {
    val pattern = NSDateFormatter.dateFormatFromTemplate("j", 0u, locale.nsLocale) ?: return false
    return pattern.split('\'').filterIndexed { index, _ -> index % 2 == 0 }.any { part ->
        part.any { it == 'h' || it == 'K' }
    }
}

/** Apple counts the week from Sunday (0); ISO from Monday (1). */
private fun DayOfWeek.appleIndex(): Int = isoDayNumber % DAYS_IN_A_WEEK

actual fun formatDayOfWeek(day: DayOfWeek, locale: Locale): String =
    formatter(locale).weekdaySymbols[day.appleIndex()] as String

actual fun formatLogTime(epochMs: Long, zone: ZoneId): String {
    val at = localDateTimeOf(epochMs, zone)
    return isoDay(at.date) + " " + twoDigits(at.hour) + ":" + twoDigits(at.minute) + ":" +
        twoDigits(at.second)
}

actual fun formatMonthAndYear(month: YearMonth, locale: Locale): String =
    patterned(locale, "LLLL yyyy").stringFromDate(noonOf(month.firstDay()))

actual fun formatMonthName(month: YearMonth, locale: Locale): String =
    patterned(locale, "LLLL").stringFromDate(noonOf(month.firstDay()))

actual fun formatShortDay(date: LocalDate, locale: Locale): String =
    patterned(locale, "EEE, MMM d").stringFromDate(noonOf(date))

actual fun formatDay(date: LocalDate, locale: Locale): String =
    styled(locale, NSDateFormatterFullStyle).stringFromDate(noonOf(date))

actual fun formatMediumDay(date: LocalDate, locale: Locale): String =
    styled(locale, NSDateFormatterMediumStyle).stringFromDate(noonOf(date))

actual fun formatDate(epochMs: Long, zone: ZoneId, locale: Locale): String =
    styled(locale, NSDateFormatterMediumStyle, zone.id).stringFromDate(moment(epochMs))

actual fun formatLongDay(date: LocalDate, locale: Locale): String =
    styled(locale, NSDateFormatterLongStyle).stringFromDate(noonOf(date))

actual fun formatFullMonthName(month: YearMonth, locale: Locale): String =
    formatter(locale).monthSymbols[month.monthNumber - 1] as String

actual fun formatShortDayOfWeek(day: DayOfWeek, locale: Locale): String =
    formatter(locale).shortWeekdaySymbols[day.appleIndex()] as String

actual fun formatHourMark(time: LocalTime, locale: Locale, twentyFourHour: Boolean): String =
    patterned(locale, if (twentyFourHour) "HH:mm" else "h a").stringFromDate(
        moment(time.toSecondOfDay() * MS_PER_SECOND.toLong()),
    )
