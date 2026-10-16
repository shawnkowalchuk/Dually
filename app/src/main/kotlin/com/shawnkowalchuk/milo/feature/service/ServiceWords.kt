package com.shawnkowalchuk.milo.feature.service

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.text.Words
import com.shawnkowalchuk.milo.core.designsystem.text.kmText
import com.shawnkowalchuk.milo.core.designsystem.text.serviceDueText
import com.shawnkowalchuk.milo.core.service.ServiceDue
import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.service.ServiceRecord
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatDate
import java.math.BigDecimal
import java.time.ZoneId
import java.util.Locale

/** The most digits of whole dollars a cost may have: $999,999.99. */
private const val MAX_DOLLAR_DIGITS = 6
private const val CENT_DIGITS = 2

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
    kmText(km, unit, locale, screenWords())

/** When [item] is due, in one line ([serviceDueText]). */
@Composable
internal fun dueWords(
    item: ServiceItem,
    due: ServiceDue,
    unit: DistanceUnit,
    locale: Locale,
): String = serviceDueText(item, due, unit, locale, screenWords())

/** The screen's own strings, as [serviceDueText] takes them. */
@Composable
private fun screenWords(): Words {
    val resources = LocalResources.current
    return { id, args -> resources.getString(id, *args) }
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
