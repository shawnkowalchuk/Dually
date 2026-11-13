package com.shawnkowalchuk.milo.core.allowance

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Cents are two digits after the point (BusinessAllowance.kt has the same rule). */
private const val CENT_DIGITS = 2

actual fun centsPerMileOf(centsPerKm: Int): Int {
    val kilometresPerMile =
        DistanceUnit.MILES.metresPerUnit.divide(DistanceUnit.KILOMETRES.metresPerUnit)
    return BigDecimal(centsPerKm)
        .multiply(kilometresPerMile)
        .setScale(0, RoundingMode.HALF_UP)
        .intValueExact()
}

actual fun formatCentsPerKm(centsPerKm: Int, locale: Locale): String =
    "$" + String.format(locale, "%.2f", BigDecimal.valueOf(centsPerKm.toLong(), CENT_DIGITS))
