package com.shawnkowalchuk.milo.core.designsystem.text

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.odometer.formatOdometer
import com.shawnkowalchuk.milo.core.service.ServiceDue
import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.absoluteValue

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
 * When [item] is due, in one line: overdue first, by distance and then by time; then due in a
 * distance, on a day, or both, whichever comes first.
 */
fun serviceDueText(
    item: ServiceItem,
    due: ServiceDue,
    unit: DistanceUnit,
    locale: Locale,
    words: Words,
): String {
    val kmLeft = due.kmLeft
    val dueOn = due.dueOn?.let { formatMediumDay(it, locale) }
    fun km(value: Long) = kmText(value, unit, locale, words)
    return when {
        kmLeft != null && kmLeft <= 0 ->
            words(R.string.service_overdue_by, arrayOf(km(kmLeft.absoluteValue)))

        due.daysLeft != null && due.daysLeft <= 0 && dueOn != null ->
            words(R.string.service_overdue_since, arrayOf(dueOn))

        kmLeft != null && dueOn != null ->
            words(R.string.service_due_in_or_on, arrayOf(km(kmLeft), dueOn))

        kmLeft != null -> words(R.string.service_due_in, arrayOf(km(kmLeft)))

        dueOn != null -> words(R.string.service_due_on, arrayOf(dueOn))

        // A distance to go by and nothing to count it from.
        else -> words(R.string.service_due_no_reading, arrayOf(km(item.everyKm?.toLong() ?: 0)))
    }
}
