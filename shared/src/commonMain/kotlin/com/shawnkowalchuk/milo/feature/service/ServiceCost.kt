package com.shawnkowalchuk.milo.feature.service

// A service's cost as typed on Done (since 2026-10-16; shared since 2026-11-14, so that the iPhone
// takes exactly what Android takes).

/** The most digits of whole dollars a cost may have: $999,999.99. */
private const val MAX_DOLLAR_DIGITS = 6
private const val CENT_DIGITS = 2
private const val CENTS_PER_DOLLAR = 100L

/**
 * A cost as typed ("59.99", "$1,200", "80"), in cents, or null if it is not one. Commas and
 * spaces between the digits are allowed; at most two decimals, and up to $999,999.99.
 */
fun parseCost(typed: String): Long? {
    val bare = typed.trim().removePrefix("$").filterNot { it == ',' || it.isWhitespace() }
    val whole = bare.substringBefore('.')
    val fraction = bare.substringAfter('.', missingDelimiterValue = "")
    val wellFormed =
        whole.length in 1..MAX_DOLLAR_DIGITS &&
            fraction.length <= CENT_DIGITS &&
            (whole + fraction).all { it in '0'..'9' }
    if (!wellFormed) return null
    return whole.toLong() * CENTS_PER_DOLLAR + fraction.padEnd(CENT_DIGITS, '0').toLong()
}
