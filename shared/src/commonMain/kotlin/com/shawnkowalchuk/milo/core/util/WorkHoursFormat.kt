package com.shawnkowalchuk.milo.core.util

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.text.formatText
import com.shawnkowalchuk.milo.core.time.DayOfWeek
import com.shawnkowalchuk.milo.core.time.LocalTime
import kotlin.math.roundToInt

// How the work schedule's tile writes three things: the short name of a day on its button, the
// name of an hour under its slider, and the number of hours a work week has. In a file of its
// own because they came with the Settings screen's layout, while other screens were being laid
// out at the same time.
private const val MINUTES_PER_HOUR = 60

private const val TENTHS = 10

/** A day of the week by its short name, for a button seven of which share a line: "Mon". */
expect fun formatShortDayOfWeek(day: DayOfWeek, locale: Locale): String

/**
 * A full hour as the short name that stands under the slider of the work hours: "4 AM", or
 * "04:00" on a phone that is set to 24 hours.
 *
 * It is shorter than `formatClockTime` writes the same moment ("4:00 AM"), because five of
 * them stand side by side under one line. The phone's own 24-hour switch decides between the
 * two, as for every time MilO writes.
 *
 * @param time the hour. Its minutes are written on a 24-hour phone and left out otherwise, so
 * it is meant for a full hour.
 */
expect fun formatHourMark(time: LocalTime, locale: Locale, twentyFourHour: Boolean): String

/**
 * A number of minutes as the hours figure shown to the user: 2550 becomes "42.5", 2400 becomes
 * "40". It is rounded to a tenth of an hour, and whole hours carry no decimal.
 *
 * Only the number is returned, like `formatMinutes`: the unit is in the string resources.
 *
 * @param locale decides the decimal separator.
 * @throws IllegalArgumentException if [minutes] is negative. Hours like that cannot be right.
 */
fun formatHours(minutes: Int, locale: Locale): String {
    require(minutes >= 0) { "A length of time cannot be negative, but was $minutes min" }
    val tenths = (minutes * TENTHS / MINUTES_PER_HOUR.toDouble()).roundToInt()
    return if (tenths % TENTHS == 0) {
        formatText(locale, "%d", tenths / TENTHS)
    } else {
        formatText(locale, "%.1f", tenths / TENTHS.toDouble())
    }
}
