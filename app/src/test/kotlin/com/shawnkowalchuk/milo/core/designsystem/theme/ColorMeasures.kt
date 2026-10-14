package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// The two measures the colour tests hold the theme to: how well one colour can be read on
// another, and how different two colours look.

/** The contrast of two colours as WCAG 2 counts it: from 1 (none) to 21 (black on white). */
internal fun contrast(a: Color, b: Color): Double {
    val lighter = max(luminance(a), luminance(b))
    val darker = min(luminance(a), luminance(b))
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * How different two colours look, as the distance between them in CIE L*a*b* (the "delta E" of
 * 1976). About 2 is the smallest difference an eye can see with the two side by side; colours
 * that are 40 apart are told apart at a glance, with nothing beside them to compare with.
 */
internal fun difference(a: Color, b: Color): Double {
    val (l1, a1, b1) = lab(a)
    val (l2, a2, b2) = lab(b)
    return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
}

private fun luminance(color: Color): Double =
    0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

private fun linear(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

// A colour of the screen (sRGB) as lightness and two colour axes, by way of CIE XYZ under the
// daylight white the screen's colours are defined for (D65).
private fun lab(color: Color): Triple<Double, Double, Double> {
    val r = linear(color.red)
    val g = linear(color.green)
    val b = linear(color.blue)
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / WHITE_X
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / WHITE_Z
    return Triple(116 * curve(y) - 16, 500 * (curve(x) - curve(y)), 200 * (curve(y) - curve(z)))
}

private fun curve(t: Double): Double = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116

private const val WHITE_X = 0.95047
private const val WHITE_Z = 1.08883
