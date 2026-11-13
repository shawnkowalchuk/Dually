package com.shawnkowalchuk.milo.core.number

// Exact decimal numbers, for the miles in a kilometre and the cents in a rate (ADR-014,
// phase 3). On Android the type is java.math.BigDecimal itself, so the app passes the numbers it
// always did and every figure is worked out as before; on the iPhone it is a small exact decimal
// of Dually's own.

/** An exact decimal number: 1609.344, never 1609.3439999. */
expect class BigDecimal

/** The number written in [text]: "1609.344". */
expect fun decimalOf(text: String): BigDecimal

/** This number divided by ten [places] times, exactly. */
expect fun BigDecimal.movedPointLeft(places: Int): BigDecimal

/** The nearest Double to this number. */
expect fun BigDecimal.asDouble(): Double

/** [a] times [b]; an ArithmeticException when the product does not fit a Long. */
expect fun multiplyExact(a: Long, b: Long): Long
