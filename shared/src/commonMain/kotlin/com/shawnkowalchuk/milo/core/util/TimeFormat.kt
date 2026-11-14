package com.shawnkowalchuk.milo.core.util

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.DayOfWeek
import com.shawnkowalchuk.milo.core.time.LocalDate
import com.shawnkowalchuk.milo.core.time.LocalTime
import com.shawnkowalchuk.milo.core.time.YearMonth
import com.shawnkowalchuk.milo.core.time.ZoneId

/**
 * Turns a stored time (wall-clock milliseconds since 1970) into the time of day shown to the
 * user: "08:14", or "8:14 a.m.".
 *
 * Times are stored as plain milliseconds and only given a time zone here, at the moment of
 * display, so a trip recorded before a clock change still shows the time it happened.
 *
 * @param zone and [locale] have no defaults on purpose, like `formatDistance`: the screen
 * passes the phone's own, and a test passes fixed ones.
 * @param twentyFourHour whether the phone is set to write times with 24 hours ("Use 24-hour
 * format" in Android's date and time settings). The phone's switch decides between "08:14" and
 * "8:14 a.m.", so that MilO's times read like the phone's own clock; the language decides how
 * either is spelled.
 */
expect fun formatTimeOfDay(
    epochMs: Long,
    zone: ZoneId,
    locale: Locale,
    twentyFourHour: Boolean,
): String

/**
 * A time of day that belongs to no date, such as the start of a work day, in the same form as
 * [formatTimeOfDay]: "08:00", or "8:00 a.m.".
 *
 * @param twentyFourHour as for [formatTimeOfDay]. The time picker's dial is drawn by the same
 * switch, so a time is never entered on a dial that disagrees with the time printed beside it.
 */
expect fun formatClockTime(time: LocalTime, locale: Locale, twentyFourHour: Boolean): String

/**
 * Whether [locale] itself writes a short time of day on a 12-hour clock. It says what the
 * language does, not what the phone is set to: [formatTimeOfDay] is told that by its caller.
 */
internal expect fun usesTwelveHourClock(locale: Locale): Boolean

/** A day of the week by its full name: "Monday". */
expect fun formatDayOfWeek(day: DayOfWeek, locale: Locale): String

/**
 * Turns a stored time into the form the event log shows: "2026-10-05 08:14:03", in [zone].
 *
 * The same form in every language, on purpose. The log is evidence for why a trip did or did not
 * start: its lines are compared with each other and with the truck's clock, to the second, so
 * the date and the 24-hour time are always written out in full.
 */
expect fun formatLogTime(epochMs: Long, zone: ZoneId): String

/**
 * A month as a heading: "October 2026". "LLLL" is the month's name standing alone, which in
 * some languages is spelled differently from the name inside a date.
 */
expect fun formatMonthAndYear(month: YearMonth, locale: Locale): String

/**
 * A month by its name alone, for a place where the year goes without saying because the month
 * is this one or the last: "October".
 */
expect fun formatMonthName(month: YearMonth, locale: Locale): String

/**
 * Today as the short line under the app's name: "Tue, Oct 6". The names of the weekday and the
 * month are the language's own short ones; their order is the design's, in every language.
 */
expect fun formatShortDay(date: LocalDate, locale: Locale): String

/** A day as a heading, in the long form of the user's language: "Monday, 5 October 2026". */
expect fun formatDay(date: LocalDate, locale: Locale): String

/**
 * A day in the medium form of the user's language, with its year: "5 Oct 2026". For a place
 * too narrow for the long form, where the year still has to be said: the two days of a date
 * range.
 */
expect fun formatMediumDay(date: LocalDate, locale: Locale): String

/**
 * A stored time as a date only, in the medium form of the user's language: "5 Oct 2026". Used
 * where the day matters and the time of day does not, such as the day a setting was confirmed.
 */
expect fun formatDate(epochMs: Long, zone: ZoneId, locale: Locale): String

/**
 * A day written out in [locale]'s long form, the way the report dates what it says:
 * "November 13, 2026" in English.
 */
expect fun formatLongDay(date: LocalDate, locale: Locale): String

/**
 * The full name of [month]'s month in [locale], as a month is named inside a sentence or a
 * table: "November". (formatMonthName gives the form that stands alone, which some languages
 * spell differently.)
 */
expect fun formatFullMonthName(month: YearMonth, locale: Locale): String

/** The month's short name in the phone's language: "Nov". The home-screen widget's dollars. */
expect fun formatShortMonthName(month: YearMonth, locale: Locale): String
