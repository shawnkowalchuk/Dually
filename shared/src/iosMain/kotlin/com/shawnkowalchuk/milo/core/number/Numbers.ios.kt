package com.shawnkowalchuk.milo.core.number

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSDecimalNumberHandler
import platform.Foundation.NSRoundingMode

// On the iPhone the shared code's exact decimals are Apple's NSDecimalNumber, which counts in
// decimal digits as java.math.BigDecimal does (ADR-014, phase 3).

/** An exact decimal number. Equal to another of the same value. */
actual class BigDecimal internal constructor(internal val ns: NSDecimalNumber) {
    override fun equals(other: Any?): Boolean = other is BigDecimal && ns.compare(other.ns) == 0L

    override fun hashCode(): Int = ns.stringValue.trimEnd('0').trimEnd('.').hashCode()

    override fun toString(): String = ns.stringValue
}

actual fun decimalOf(text: String): BigDecimal = BigDecimal(NSDecimalNumber(string = text))

actual fun BigDecimal.movedPointLeft(places: Int): BigDecimal =
    BigDecimal(ns.decimalNumberByMultiplyingByPowerOf10((-places).toShort()))

/**
 * The nearest Double, as java.math.BigDecimal gives it. Read from the decimal's digits: Apple's
 * own doubleValue can land one step off (1609.3439999999998 for 1609.344).
 */
actual fun BigDecimal.asDouble(): Double = ns.stringValue.toDouble()

actual fun multiplyExact(a: Long, b: Long): Long {
    val product = a * b
    val overflowed =
        (a != 0L && (product / a != b || (a == -1L && b == Long.MIN_VALUE)))
    if (overflowed) throw ArithmeticException("long overflow")
    return product
}

/** How a quotient is rounded: to the nearest, a half up, or towards zero. */
internal enum class Rounding(val mode: NSRoundingMode) {
    HALF_UP(NSRoundingMode.NSRoundPlain),
    DOWN(NSRoundingMode.NSRoundDown),
}

/** The number a Double prints as, exactly: 2.675 is 2.675 (java's BigDecimal.valueOf). */
internal fun decimalOfDouble(value: Double): BigDecimal = decimalOf(value.toString())

/** The whole number [value] divided by ten [scale] times: (125, 1) is 12.5. */
internal fun decimalOf(value: Long, scale: Int): BigDecimal =
    decimalOf(value.toString()).movedPointLeft(scale)

internal fun BigDecimal.times(other: BigDecimal): BigDecimal =
    BigDecimal(ns.decimalNumberByMultiplyingBy(other.ns))

/** This divided by [divisor], kept to [scale] decimals, rounded by [rounding]. */
internal fun BigDecimal.dividedBy(divisor: BigDecimal, scale: Int, rounding: Rounding): BigDecimal {
    val handler =
        NSDecimalNumberHandler(
            roundingMode = rounding.mode,
            scale = scale.toShort(),
            raiseOnExactness = false,
            raiseOnOverflow = true,
            raiseOnUnderflow = true,
            raiseOnDivideByZero = true,
        )
    return BigDecimal(ns.decimalNumberByDividingBy(divisor.ns, withBehavior = handler))
}

/** This rounded to [scale] decimals by [rounding]. */
internal fun BigDecimal.rounded(scale: Int, rounding: Rounding): BigDecimal =
    dividedBy(decimalOf("1"), scale, rounding)

/** This as a Long; it must be whole. */
internal fun BigDecimal.wholeLong(): Long {
    val text = ns.stringValue
    require('.' !in text) { "$text is not a whole number" }
    return text.toLong()
}
