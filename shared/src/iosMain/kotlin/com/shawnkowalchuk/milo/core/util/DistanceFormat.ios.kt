package com.shawnkowalchuk.milo.core.util

import com.shawnkowalchuk.milo.core.number.BigDecimal
import com.shawnkowalchuk.milo.core.number.Rounding
import com.shawnkowalchuk.milo.core.number.asDouble
import com.shawnkowalchuk.milo.core.number.decimalOf
import com.shawnkowalchuk.milo.core.number.decimalOfDouble
import com.shawnkowalchuk.milo.core.number.dividedBy
import com.shawnkowalchuk.milo.core.number.multiplyExact
import com.shawnkowalchuk.milo.core.number.times
import com.shawnkowalchuk.milo.core.number.wholeLong
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.text.formatText
import kotlin.math.roundToLong

// The iPhone's distances, worked out step for step as the Android ones are
// (DistanceFormat.android.kt), with Apple's exact decimals in java.math.BigDecimal's place.

private const val METRES_PER_TENTH_OF_A_KILOMETRE = 100.0
private const val HUNDREDTHS = 2
private const val TENTHS_PER_WHOLE = 10L

private fun requireDistance(metres: Double) = require(metres.isFinite() && metres >= 0.0) {
    "A distance must be a finite, non-negative number of metres, but was $metres"
}

actual fun tenthsOf(metres: Double, unit: DistanceUnit): Long {
    requireDistance(metres)
    return when (unit) {
        // Math.round: a half goes up, as roundToLong does.
        DistanceUnit.KILOMETRES -> (metres / METRES_PER_TENTH_OF_A_KILOMETRE).roundToLong()

        DistanceUnit.MILES ->
            decimalOfDouble(metres).dividedBy(unit.metresPerTenth, 0, Rounding.HALF_UP).wholeLong()
    }
}

actual fun formatTenths(tenths: Long, locale: Locale): String =
    formatText(locale, "%.1f", decimalOf(tenths, 1))

actual fun metresOfTenths(tenths: Long, unit: DistanceUnit): Double = when (unit) {
    DistanceUnit.KILOMETRES -> tenths * METRES_PER_TENTH_OF_A_KILOMETRE
    DistanceUnit.MILES -> decimalOf(tenths, 0).times(unit.metresPerTenth).asDouble()
}

actual fun formatShortDistance(metres: Double, unit: DistanceUnit, locale: Locale): String =
    when (unit) {
        DistanceUnit.KILOMETRES -> formatDistance(metres, unit, locale)

        DistanceUnit.MILES -> {
            requireDistance(metres)
            val miles = decimalOfDouble(
                metres,
            ).dividedBy(unit.metresPerUnit, HUNDREDTHS, Rounding.HALF_UP)
            formatText(locale, "%.${HUNDREDTHS}f", miles)
        }
    }

actual fun metresOf(typed: BigDecimal, unit: DistanceUnit): Double =
    typed.times(unit.metresPerUnit).asDouble()

actual fun tenthsOfWhole(whole: Long, from: DistanceUnit, to: DistanceUnit): Long = if (from ==
    to
) {
    multiplyExact(whole, TENTHS_PER_WHOLE)
} else {
    tenthsOf(metresOf(decimalOf(whole, 0), from), to)
}

actual fun wholeUnitsBelow(metres: Double, unit: DistanceUnit): Long =
    decimalOfDouble(metres).dividedBy(unit.metresPerUnit, 0, Rounding.DOWN).wholeLong()
