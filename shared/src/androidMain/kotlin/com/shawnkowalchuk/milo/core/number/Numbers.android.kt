package com.shawnkowalchuk.milo.core.number

// On Android the shared code's exact decimals are java.math.BigDecimal itself (ADR-014,
// phase 3).

actual typealias BigDecimal = java.math.BigDecimal

actual fun decimalOf(text: String): BigDecimal = BigDecimal(text)

actual fun BigDecimal.movedPointLeft(places: Int): BigDecimal = movePointLeft(places)

actual fun BigDecimal.asDouble(): Double = toDouble()

actual fun multiplyExact(a: Long, b: Long): Long = Math.multiplyExact(a, b)
