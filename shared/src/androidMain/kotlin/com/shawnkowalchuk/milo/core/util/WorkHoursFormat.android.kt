package com.shawnkowalchuk.milo.core.util

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** An hour by itself on a phone set to AM and PM: "4 AM". */
private const val TWELVE_HOUR_MARK = "h a"

/** And on a phone set to 24 hours: "04:00". */
private const val TWENTY_FOUR_HOUR_MARK = "HH:mm"

actual fun formatShortDayOfWeek(day: DayOfWeek, locale: Locale): String =
    day.getDisplayName(TextStyle.SHORT, locale)

actual fun formatHourMark(time: LocalTime, locale: Locale, twentyFourHour: Boolean): String {
    val pattern = if (twentyFourHour) TWENTY_FOUR_HOUR_MARK else TWELVE_HOUR_MARK
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}
