package com.shawnkowalchuk.milo.core.allowance

import com.shawnkowalchuk.milo.core.number.Rounding
import com.shawnkowalchuk.milo.core.number.decimalOf
import com.shawnkowalchuk.milo.core.number.dividedBy
import com.shawnkowalchuk.milo.core.number.rounded
import com.shawnkowalchuk.milo.core.number.times
import com.shawnkowalchuk.milo.core.number.wholeLong
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.text.formatText
import com.shawnkowalchuk.milo.core.util.DistanceUnit

// The iPhone's rate per mile and its words, as the Android ones (BusinessAllowance.android.kt).

private const val CENT_DIGITS = 2
private const val EXACT_SCALE = 10

actual fun centsPerMileOf(centsPerKm: Int): Int {
    val kilometresPerMile =
        DistanceUnit.MILES.metresPerUnit.dividedBy(
            DistanceUnit.KILOMETRES.metresPerUnit,
            EXACT_SCALE,
            Rounding.HALF_UP,
        )
    return decimalOf(centsPerKm.toLong(), 0)
        .times(kilometresPerMile)
        .rounded(0, Rounding.HALF_UP)
        .wholeLong()
        .toInt()
}

actual fun formatCentsPerKm(centsPerKm: Int, locale: Locale): String =
    "$" + formatText(locale, "%.2f", decimalOf(centsPerKm.toLong(), CENT_DIGITS))
