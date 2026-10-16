package com.shawnkowalchuk.milo.feature.service

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.text.distanceRes
import com.shawnkowalchuk.milo.core.odometer.formatOdometer
import com.shawnkowalchuk.milo.core.service.ServiceDue
import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.service.ServiceRecord
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatDate
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId
import java.util.Locale
import kotlin.math.absoluteValue

/** Metres in a kilometre: the book counts in kilometres, the screen in the unit chosen. */
private val METRES_PER_KM = BigDecimal(1000)

/** The most digits of whole dollars a cost may have: $999,999.99. */
private const val MAX_DOLLAR_DIGITS = 6
private const val CENT_DIGITS = 2

/** [km] in [unit], whole: what the screen writes. */
internal fun kmToShown(km: Long, unit: DistanceUnit): Long =
    BigDecimal(km).multiply(METRES_PER_KM).divide(unit.metresPerUnit, 0, RoundingMode.HALF_UP)
        .toLong()

/** A whole figure typed in [unit], in kilometres: what the book keeps. */
internal fun shownToKm(value: Long, unit: DistanceUnit): Long =
    BigDecimal(value).multiply(unit.metresPerUnit).divide(METRES_PER_KM, 0, RoundingMode.HALF_UP)
        .toLong()

/**
 * A cost as typed ("59.99", "$1,200", "80"), in cents, or null if it is not one. Commas and
 * spaces between the digits are allowed; at most two decimals, and up to $999,999.99.
 */
internal fun parseCost(typed: String): Long? {
    val bare = typed.trim().removePrefix("$").filterNot { it == ',' || it.isWhitespace() }
    val whole = bare.substringBefore('.')
    val fraction = bare.substringAfter('.', missingDelimiterValue = "")
    val wellFormed =
        whole.length in 1..MAX_DOLLAR_DIGITS &&
            fraction.length <= CENT_DIGITS &&
            (whole + fraction).all { it in '0'..'9' }
    if (!wellFormed) return null
    return BigDecimal("$whole.${fraction.padEnd(CENT_DIGITS, '0')}").movePointRight(CENT_DIGITS)
        .longValueExact()
}

/** A cost in cents as dollars: "$59.99". */
internal fun formatCost(cents: Long, locale: Locale): String =
    "$" + String.format(locale, "%,.2f", BigDecimal.valueOf(cents, CENT_DIGITS))

/** An odometer figure in kilometres, written in [unit]: "123,456 km". */
@Composable
internal fun odometerWords(km: Long, unit: DistanceUnit, locale: Locale): String =
    stringResource(distanceRes(unit), formatOdometer(kmToShown(km, unit), locale))

/**
 * When [item] is due, in one line: overdue first, by distance and then by time; then due in a
 * distance, on a day, or both, whichever comes first.
 */
@Composable
internal fun dueWords(
    item: ServiceItem,
    due: ServiceDue,
    unit: DistanceUnit,
    locale: Locale,
): String {
    val kmLeft = due.kmLeft
    val dueOn = due.dueOn?.let { formatMediumDay(it, locale) }
    return when {
        kmLeft != null && kmLeft <= 0 ->
            stringResource(
                R.string.service_overdue_by,
                odometerWords(kmLeft.absoluteValue, unit, locale),
            )

        due.daysLeft != null && due.daysLeft <= 0 && dueOn != null ->
            stringResource(R.string.service_overdue_since, dueOn)

        kmLeft != null && dueOn != null ->
            stringResource(
                R.string.service_due_in_or_on,
                odometerWords(kmLeft, unit, locale),
                dueOn,
            )

        kmLeft != null -> stringResource(
            R.string.service_due_in,
            odometerWords(kmLeft, unit, locale),
        )

        dueOn != null -> stringResource(R.string.service_due_on, dueOn)

        // A distance to go by and nothing to count it from.
        else -> stringResource(
            R.string.service_due_no_reading,
            odometerWords(item.everyKm?.toLong() ?: 0, unit, locale),
        )
    }
}

/** When [item] was last done, and at what odometer: "Last done Oct 16, 2026 at 123,456 km". */
@Composable
internal fun lastDoneWords(
    item: ServiceItem,
    unit: DistanceUnit,
    locale: Locale,
    zone: ZoneId,
): String {
    val day = formatDate(item.lastDoneAtMs, zone, locale)
    return item.lastDoneKm?.let {
        stringResource(R.string.service_last_done_at, day, odometerWords(it, unit, locale))
    } ?: stringResource(R.string.service_last_done, day)
}

/** One line of the history: the day, the odometer, the cost and the note, those there are. */
@Composable
internal fun recordWords(
    record: ServiceRecord,
    unit: DistanceUnit,
    locale: Locale,
    zone: ZoneId,
): String = listOfNotNull(
    formatDate(record.doneAtMs, zone, locale),
    record.odometerKm?.let { odometerWords(it, unit, locale) },
    record.costCents?.let { formatCost(it, locale) },
    record.note,
).joinToString(" · ")
