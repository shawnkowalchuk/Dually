package com.shawnkowalchuk.milo.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// How a stored distance becomes a figure Shawn or the accountant reads, and how such figures
// are added up. One rule for every screen and for the report, so that no two of them can show
// a different total for the same trips.
//
// Since 2026-10-07 the rule holds in either unit ([DistanceUnit]): a trip is rounded once to a
// tenth of the unit it is shown in, from its stored metres, and a total is the sum of those
// rounded figures. So a month's total in miles is the sum of the miles printed for its trips.
// It is not its kilometre total converted, and it must not be: the column would not add up.
private const val METRES_PER_TENTH_OF_A_KILOMETRE = 100.0

/** Two decimals: what a short distance in miles is written with ([formatShortDistance]). */
private const val HUNDREDTHS = 2

private const val TENTHS_PER_WHOLE = 10L

actual fun tenthsOf(metres: Double, unit: DistanceUnit): Long {
    require(metres.isFinite() && metres >= 0.0) {
        "A distance must be a finite, non-negative number of metres, but was $metres"
    }
    return when (unit) {
        DistanceUnit.KILOMETRES -> Math.round(metres / METRES_PER_TENTH_OF_A_KILOMETRE)

        DistanceUnit.MILES ->
            BigDecimal
                .valueOf(metres)
                .divide(unit.metresPerTenth, 0, RoundingMode.HALF_UP)
                .longValueExact()
    }
}

actual fun formatTenths(tenths: Long, locale: Locale): String =
    String.format(locale, "%.1f", BigDecimal.valueOf(tenths, 1))

actual fun metresOfTenths(tenths: Long, unit: DistanceUnit): Double = when (unit) {
    DistanceUnit.KILOMETRES -> tenths * METRES_PER_TENTH_OF_A_KILOMETRE
    DistanceUnit.MILES -> BigDecimal.valueOf(tenths).multiply(unit.metresPerTenth).toDouble()
}

actual fun formatShortDistance(metres: Double, unit: DistanceUnit, locale: Locale): String =
    when (unit) {
        DistanceUnit.KILOMETRES -> formatDistance(metres, unit, locale)

        DistanceUnit.MILES -> {
            require(metres.isFinite() && metres >= 0.0) {
                "A distance must be a finite, non-negative number of metres, but was $metres"
            }
            val miles =
                BigDecimal
                    .valueOf(metres)
                    .divide(unit.metresPerUnit, HUNDREDTHS, RoundingMode.HALF_UP)
            String.format(locale, "%.${HUNDREDTHS}f", miles)
        }
    }

actual fun metresOf(typed: BigDecimal, unit: DistanceUnit): Double =
    typed.multiply(unit.metresPerUnit).toDouble()

actual fun tenthsOfWhole(whole: Long, from: DistanceUnit, to: DistanceUnit): Long = if (from ==
    to
) {
    Math.multiplyExact(whole, TENTHS_PER_WHOLE)
} else {
    tenthsOf(metresOf(BigDecimal.valueOf(whole), from), to)
}

actual fun wholeUnitsBelow(metres: Double, unit: DistanceUnit): Long =
    BigDecimal.valueOf(metres).divide(unit.metresPerUnit, 0, RoundingMode.DOWN).longValueExact()
