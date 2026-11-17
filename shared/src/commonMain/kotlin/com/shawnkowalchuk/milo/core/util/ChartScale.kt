package com.shawnkowalchuk.milo.core.util

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** How many steps a chart's scale is cut into: four lines of the grid above its zero. */
const val CHART_GRID_STEPS = 4

/** The steps a scale may take, times a power of ten: each a figure one reads at a glance. */
private val NICE_STEPS = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)

/**
 * One step of a chart's scale for a highest value of [max]: the smallest round step, at least 1
 * and a whole number, whose [CHART_GRID_STEPS] steps reach [max]. The grid's lines then stand on
 * whole, round figures ("0, 150, 300, 450, 600"); a chart with nothing on it counts 0 to 4.
 *
 * Shared since 2026-11-16, for the iPhone's graph: both phones draw the same scale.
 */
fun chartStep(max: Double): Double {
    val wanted = (max / CHART_GRID_STEPS).coerceAtLeast(1.0)
    val magnitude = 10.0.pow(floor(log10(wanted)))
    return NICE_STEPS
        .map { it * magnitude }
        .first { it >= wanted && it == floor(it) }
}
