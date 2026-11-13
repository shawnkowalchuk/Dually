package com.shawnkowalchuk.milo.core.text

import com.shawnkowalchuk.milo.core.number.BigDecimal
import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

// On the iPhone the shared code's language is an NSLocale identifier, and Java's format
// patterns are filled in here, for the part of them Dually's words use (ADR-014, phase 3).

/** A language and region, by Apple's identifier: "en_CA". Equal when the identifiers are. */
actual class Locale(val identifier: String) {
    internal val nsLocale: NSLocale get() = NSLocale(localeIdentifier = identifier)

    override fun equals(other: Any?): Boolean = other is Locale && other.identifier == identifier

    override fun hashCode(): Int = identifier.hashCode()

    override fun toString(): String = identifier
}

/** The neutral language: digits 0 to 9, a dot before decimals, a comma between thousands. */
actual val rootLocale: Locale = Locale("en_US_POSIX")

/** The language the iPhone is set to now. */
fun currentLocale(): Locale = Locale(NSLocale.currentLocale.localeIdentifier)

private const val DEFAULT_DECIMALS = 6

/**
 * [pattern] with [args] put in, as Java's `String.format` would: `%s`, `%d`, `%,d`, `%.1f`,
 * `%,.2f`, an argument's number (`%2$s`), a width (`%5d`, `%-5s`, `%05d`), `%%` and `%n`.
 * Anything else is refused, so that a pattern no one tested on the iPhone fails loudly.
 */
actual fun formatText(locale: Locale, pattern: String, vararg args: Any?): String {
    val out = StringBuilder()
    var next = 0
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c != '%') {
            out.append(c)
            i++
            continue
        }
        val spec = SPEC.matchAt(pattern, i) ?: error("Unsupported format at $i in \"$pattern\"")
        val (indexText, flags, widthText, precisionText, conversion) = spec.destructured
        i = spec.range.last + 1
        when (conversion) {
            "%" -> {
                out.append('%')
                continue
            }

            "n" -> {
                out.append('\n')
                continue
            }
        }
        val arg = if (indexText.isNotEmpty()) args[indexText.toInt() - 1] else args[next++]
        val text =
            when (conversion) {
                "s" -> arg.toString()

                "d" -> number(locale, arg.asWhole(), decimals = 0, grouped = ',' in flags)

                "f" -> number(
                    locale,
                    arg.asDecimal(),
                    decimals = precisionText.toIntOrNull() ?: DEFAULT_DECIMALS,
                    grouped = ',' in flags,
                )

                else -> error("Unsupported conversion %$conversion in \"$pattern\"")
            }
        out.append(padded(text, widthText.toIntOrNull() ?: 0, flags))
    }
    return out.toString()
}

private val SPEC = Regex("""%(?:(\d+)\$)?([-,0]*)(\d*)(?:\.(\d+))?([sdf%n])""")

private fun padded(text: String, width: Int, flags: String): String = when {
    text.length >= width -> text

    '-' in flags -> text.padEnd(width)

    '0' in flags -> {
        val negative = text.startsWith('-')
        val digits = if (negative) text.substring(1) else text
        (if (negative) "-" else "") + digits.padStart(width - (if (negative) 1 else 0), '0')
    }

    else -> text.padStart(width)
}

private fun Any?.asWhole(): NSNumber = when (this) {
    is Int -> NSNumber(long = toLong())
    is Long -> NSNumber(long = this)
    is Short -> NSNumber(long = toLong())
    is Byte -> NSNumber(long = toLong())
    else -> error("%d needs a whole number, but was $this")
}

/**
 * A number as Java's %f reads it: a Double by its shortest decimal form (2.675 is 2.675, not the
 * 2.67499… the binary number holds), a BigDecimal exactly.
 */
private fun Any?.asDecimal(): NSNumber = when (this) {
    is BigDecimal -> ns
    is Double -> NSDecimalNumber(string = toString())
    is Float -> NSDecimalNumber(string = toString())
    is Int -> NSDecimalNumber(string = toString())
    is Long -> NSDecimalNumber(string = toString())
    else -> error("%f needs a number, but was $this")
}

private fun number(locale: Locale, value: NSNumber, decimals: Int, grouped: Boolean): String {
    val formatter = NSNumberFormatter()
    formatter.locale = locale.nsLocale
    formatter.numberStyle = NSNumberFormatterDecimalStyle
    formatter.usesGroupingSeparator = grouped
    formatter.minimumFractionDigits = decimals.toULong()
    formatter.maximumFractionDigits = decimals.toULong()
    formatter.roundingMode = NSNumberFormatterRoundHalfUp
    return formatter.stringFromNumber(value) ?: error("Could not write $value")
}
