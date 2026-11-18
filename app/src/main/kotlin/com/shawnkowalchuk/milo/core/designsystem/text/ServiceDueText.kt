package com.shawnkowalchuk.milo.core.designsystem.text

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.odometer.formatOdometer
import com.shawnkowalchuk.milo.core.service.DueWording
import com.shawnkowalchuk.milo.core.service.ServiceDue
import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.service.dueLine
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// When a service item is due, in words (since 2026-10-16). In one place because three write it:
// the Service screen, its notification and Home's tile. The words are handed in as a function
// of a string's id and its arguments: `getString` for the notification, the composition's
// resources for a screen.

/** Metres in a kilometre: the service book counts in kilometres, the screen in the unit chosen. */
private val METRES_PER_KM = BigDecimal(1000)

/** A string's id and its arguments, written out: `Context.getString` or the screen's own. */
typealias Words = (id: Int, args: Array<out Any>) -> String

/** [km] in [unit], whole: what is written. */
fun kmToShown(km: Long, unit: DistanceUnit): Long =
    BigDecimal(km).multiply(METRES_PER_KM).divide(unit.metresPerUnit, 0, RoundingMode.HALF_UP)
        .toLong()

/** A whole figure typed in [unit], in kilometres: what the book keeps. */
fun shownToKm(value: Long, unit: DistanceUnit): Long =
    BigDecimal(value).multiply(unit.metresPerUnit).divide(METRES_PER_KM, 0, RoundingMode.HALF_UP)
        .toLong()

/** An odometer figure or a distance in kilometres, written in [unit]: "123,456 km". */
fun kmText(km: Long, unit: DistanceUnit, locale: Locale, words: Words): String =
    words(distanceRes(unit), arrayOf(formatOdometer(kmToShown(km, unit), locale)))

/**
 * When [item] is due, in one line: the sentence [dueLine] chooses, in Android's words.
 */
fun serviceDueText(
    item: ServiceItem,
    due: ServiceDue,
    unit: DistanceUnit,
    locale: Locale,
    words: Words,
): String {
    val line = dueLine(item, due)
    val km = line.km?.let { kmText(it, unit, locale, words) }
    val day = line.day?.let { formatMediumDay(it, locale) }
    return when (line.wording) {
        DueWording.OVERDUE_BY -> words(R.string.service_overdue_by, arrayOf(km.orEmpty()))

        DueWording.OVERDUE_SINCE -> words(R.string.service_overdue_since, arrayOf(day.orEmpty()))

        DueWording.DUE_IN_OR_ON ->
            words(R.string.service_due_in_or_on, arrayOf(km.orEmpty(), day.orEmpty()))

        DueWording.DUE_IN -> words(R.string.service_due_in, arrayOf(km.orEmpty()))

        DueWording.DUE_ON -> words(R.string.service_due_on, arrayOf(day.orEmpty()))

        DueWording.NO_READING -> words(R.string.service_due_no_reading, arrayOf(km.orEmpty()))
    }
}
