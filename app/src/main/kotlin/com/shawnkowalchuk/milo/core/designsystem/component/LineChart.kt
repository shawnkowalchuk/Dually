package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloChartColors
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** How high the plot is drawn, its axis labels included. */
private val ChartHeight = 220.dp

/** Room at the start for the scale's figures, and at the end for each line's name. */
private val StartRoom = 44.dp
private val EndRoom = 64.dp

/** Room under the plot for the labels of the points ("J", "F" …). */
private val LabelRoom = 22.dp

/** A line is 2 dp, a point 4 dp across its radius: 8 dp, the smallest mark one can see. */
private val LineWidth = 2.dp
private val PointRadius = 4.dp

/** A ring of the tile's colour round each point, so that two points that meet stay two. */
private val RingWidth = 2.dp

/** The size of a legend's swatch. */
private val SwatchSize = 10.dp

/** The grid's and the guide's lines; the gap from a point to its line's name; under the plot. */
private val HairLine = 1.dp
private val NameGap = 8.dp
private val LabelGap = 4.dp

/** How many steps the scale is cut into: four lines of the grid above its zero. */
private const val GRID_STEPS = 4

/**
 * One line of a [LineChart].
 *
 * @param name what it is, for the legend and the label at its end: "Business".
 * @param values one for each of the chart's points, in their order; null where there is none
 * yet (a month that has not come), where the line stops.
 */
@Immutable
data class ChartLine(val name: String, val values: List<Double?>)

/** The steps a scale may take, times a power of ten: each a figure one reads at a glance. */
private val NICE_STEPS = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)

/**
 * One step of a chart's scale for a highest value of [max]: the smallest round step, at least 1
 * and a whole number, whose [GRID_STEPS] steps reach [max]. The grid's lines then stand on
 * whole, round figures ("0, 150, 300, 450, 600"); a chart with nothing on it counts 0 to 4.
 */
fun chartStep(max: Double): Double {
    val wanted = (max / GRID_STEPS).coerceAtLeast(1.0)
    val magnitude = 10.0.pow(floor(log10(wanted)))
    return NICE_STEPS
        .map { it * magnitude }
        .first { it >= wanted && it == floor(it) }
}

/**
 * A chart of a few lines over the same points (since 2026-10-16, the Trips screen's graph of a
 * year's months): a legend over it, the lines in [MiloChartColors] in their fixed order, each
 * named at its end as well, so that no line is told apart by its colour alone; a quiet grid on
 * round figures from zero; and under each point its short label. One scale only.
 *
 * A press picks the nearest point: a guide line stands over it and its points grow, and
 * [onSelect] is told, so that the screen can say its figures in words. The screen also lists
 * every figure, which is the chart's table for whoever does not read the picture.
 *
 * @param labels one short label for each point ("J"), and [spokenSummary] what a screen reader
 * is told of the whole chart.
 * @param selected the point picked, or null.
 * @param formatValue writes a figure of the scale ("400").
 */
@Composable
fun LineChart(
    labels: List<String>,
    lines: List<ChartLine>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    formatValue: (Double) -> String,
    spokenSummary: String,
    modifier: Modifier = Modifier,
) {
    val colors = MiloChartColors.series
    Column(modifier = modifier.fillMaxWidth()) {
        Legend(lines.mapIndexed { index, line -> line.name to colors[index] })
        val measurer = rememberTextMeasurer()
        val labelStyle =
            MiloTheme.textStyles.tileLabel.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        val tile = MaterialTheme.colorScheme.surfaceContainer
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(ChartHeight)
                    .semantics { contentDescription = spokenSummary }
                    .pointerInput(labels.size) {
                        detectTapGestures { tap ->
                            val left = StartRoom.toPx()
                            val width = size.width - left - EndRoom.toPx()
                            val step = width / (labels.size - 1).coerceAtLeast(1)
                            val index = round((tap.x - left) / step).toInt()
                            onSelect(index.coerceIn(0, labels.lastIndex))
                        }
                    },
        ) {
            val max = lines.flatMap { it.values }.filterNotNull().maxOrNull() ?: 0.0
            val top = chartStep(max) * GRID_STEPS
            val plot =
                Plot(
                    left = StartRoom.toPx(),
                    right = size.width - EndRoom.toPx(),
                    top = labelStyle.fontSize.toPx(),
                    bottom = size.height - LabelRoom.toPx(),
                    points = labels.size,
                    scaleTop = top,
                )
            drawGrid(plot, measurer, labelStyle, formatValue)
            drawLabels(plot, labels, measurer, labelStyle)
            selected?.let { index ->
                val x = plot.x(index)
                drawLine(
                    MiloChartColors.guide,
                    Offset(x, plot.top),
                    Offset(x, plot.bottom),
                    HairLine.toPx(),
                )
            }
            val nameTops = nameTops(plot, lines, measurer.measure("Ag", labelStyle).size.height)
            lines.forEachIndexed { index, line ->
                drawSeries(
                    plot,
                    line,
                    colors[index],
                    selected,
                    tile,
                    measurer,
                    labelStyle,
                    nameTops[index],
                )
            }
        }
    }
}

/** The legend: each line's swatch and name, in one row. */
@Composable
private fun Legend(entries: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap)) {
        for ((name, color) in entries) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
            ) {
                Box(modifier = Modifier.size(SwatchSize).clip(CircleShape).background(color))
                Text(text = name, style = MiloTheme.textStyles.tileLabel)
            }
        }
    }
}

/** Where the plot stands on the canvas, and its scale: zero at [bottom], [scaleTop] at [top]. */
private class Plot(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val points: Int,
    val scaleTop: Double,
) {
    fun x(index: Int): Float = left + (right - left) * index / (points - 1).coerceAtLeast(1)

    fun y(value: Double): Float = bottom - ((bottom - top) * (value / scaleTop)).toFloat()
}

private fun DrawScope.drawGrid(
    plot: Plot,
    measurer: TextMeasurer,
    style: TextStyle,
    formatValue: (Double) -> String,
) {
    for (step in 0..GRID_STEPS) {
        val value = plot.scaleTop * step / GRID_STEPS
        val y = plot.y(value)
        drawLine(MiloChartColors.grid, Offset(plot.left, y), Offset(plot.right, y), HairLine.toPx())
        val text = measurer.measure(formatValue(value), style)
        drawText(text, topLeft = Offset(0f, y - text.size.height / 2f))
    }
}

private fun DrawScope.drawLabels(
    plot: Plot,
    labels: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    labels.forEachIndexed { index, label ->
        val text = measurer.measure(label, style)
        drawText(
            text,
            topLeft = Offset(plot.x(index) - text.size.width / 2f, plot.bottom + LabelGap.toPx()),
        )
    }
}

/** One line: its path where it has values, its points, and its name after its last point. */
private fun DrawScope.drawSeries(
    plot: Plot,
    line: ChartLine,
    color: Color,
    selected: Int?,
    tile: Color,
    measurer: TextMeasurer,
    style: TextStyle,
    nameTop: Float?,
) {
    val points =
        line.values.mapIndexedNotNull { index, value ->
            value?.let { index to Offset(plot.x(index), plot.y(it)) }
        }
    if (points.isEmpty()) return
    val path = Path()
    points.forEachIndexed { at, (_, point) ->
        if (at == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, color, style = Stroke(width = LineWidth.toPx()))
    for ((index, point) in points) {
        val radius = PointRadius.toPx() * if (index == selected) 1.5f else 1f
        drawCircle(tile, radius + RingWidth.toPx(), point)
        drawCircle(color, radius, point)
    }
    val last = points.last().second
    val name = measurer.measure(line.name, style)
    drawText(
        name,
        topLeft = Offset(last.x + NameGap.toPx(), nameTop ?: (last.y - name.size.height / 2f)),
    )
}

/**
 * Where each line's name stands, its top edge: beside its last point, and pushed apart where two
 * would cover each other, as two lines that end on the same figure do. Names move up, never down,
 * so that none stands on the labels under the plot. Null for a line with no point.
 */
private fun nameTops(plot: Plot, lines: List<ChartLine>, lineHeight: Int): List<Float?> {
    val ends =
        lines.mapIndexedNotNull { index, line ->
            line.values.lastOrNull { it != null }?.let { index to plot.y(it) - lineHeight / 2f }
        }.sortedByDescending { it.second }
    val placed = mutableMapOf<Int, Float>()
    var above = plot.bottom - lineHeight / 2f
    for ((index, wanted) in ends) {
        val top = minOf(wanted, above)
        placed[index] = top
        above = top - lineHeight
    }
    return lines.indices.map { placed[it] }
}
