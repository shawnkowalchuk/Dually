package com.shawnkowalchuk.milo.feature.tripgraph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.ChartLine
import com.shawnkowalchuk.milo.core.designsystem.component.LineChart
import com.shawnkowalchuk.milo.core.designsystem.component.SteppedTitle
import com.shawnkowalchuk.milo.core.designsystem.component.StepperButton
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.text.distanceRes
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatMonthName
import com.shawnkowalchuk.milo.core.util.formatTenths
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

/** Tenths in one unit: a point stands at its tenths over this. */
private const val TENTHS = 10.0

/**
 * The Trips screen's graph (since 2026-10-16; Shawn: "lets add a graph page that we can choose to
 * show or comapre different months. show on the graph the buseinss and personal mileage on
 * seperate lines"; he chose a year's months side by side, opened by a button on Trips). The year
 * with two steps, the chart, what the picked month adds up to, and every month's two figures
 * under it: the chart's table, for whoever does not read the picture.
 *
 * @param onBack leads back to Trips.
 */
@Composable
fun TripGraphScreen(
    viewModel: TripGraphViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    val spacing = MiloTheme.spacing
    TileColumn(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.small),
    ) {
        AppHeader(
            title = stringResource(R.string.graph_title),
            onBack = onBack,
            modifier = Modifier.padding(bottom = spacing.extraSmall),
        )
        SteppedTitle(
            text = state.year.toString(),
            previous =
                StepperButton(
                    description = stringResource(R.string.graph_previous_year),
                    onClick = viewModel::onPreviousYear,
                ),
            next =
                StepperButton(
                    description = stringResource(R.string.graph_next_year),
                    onClick = viewModel::onNextYear,
                    enabled = state.canStepForward,
                ),
        )
        val figures = state.figures
        if (figures == null) {
            Tile(modifier = Modifier.fillMaxWidth()) {
                Quiet(stringResource(R.string.trips_reading))
            }
        } else {
            ChartTile(state, figures, locale, viewModel::onSelect)
            MonthsTile(state, figures, locale)
        }
    }
}

@Composable
private fun ChartTile(
    state: TripGraphState,
    figures: List<MonthFigure>,
    locale: Locale,
    onSelect: (Int) -> Unit,
) {
    val business = stringResource(R.string.graph_business)
    val personal = stringResource(R.string.graph_personal)
    val whole = NumberFormat.getIntegerInstance(locale)
    Tile(modifier = Modifier.fillMaxWidth()) {
        LineChart(
            labels = figures.map { it.month.month.getDisplayName(TextStyle.NARROW, locale) },
            lines =
                listOf(
                    ChartLine(business, figures.map { it.businessTenths?.div(TENTHS) }),
                    ChartLine(personal, figures.map { it.personalTenths?.div(TENTHS) }),
                ),
            selected = state.selected,
            // A month that has not begun has no point to pick.
            onSelect = { index -> if (figures[index].businessTenths != null) onSelect(index) },
            formatValue = { whole.format(it) },
            spokenSummary = stringResource(R.string.graph_spoken, state.year.toString()),
        )
        state.selected?.let { figures.getOrNull(it) }?.takeIf { it.businessTenths != null }?.let {
            Text(
                text = monthLine(it, state, locale),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun MonthsTile(state: TripGraphState, figures: List<MonthFigure>, locale: Locale) {
    Tile(modifier = Modifier.fillMaxWidth()) {
        val shown = figures.filter { it.businessTenths != null }
        shown.forEachIndexed { index, figure ->
            Text(text = monthLine(figure, state, locale), style = MiloTheme.textStyles.sentence)
            if (index < shown.lastIndex) HorizontalDivider()
        }
    }
}

/** "March: Business 412.3 km · Personal 88.0 km". */
@Composable
private fun monthLine(figure: MonthFigure, state: TripGraphState, locale: Locale): String {
    fun distance(tenths: Long?): String = formatTenths(tenths ?: 0, locale)
    return stringResource(
        R.string.graph_month_line,
        formatMonthName(figure.month, locale),
        stringResource(distanceRes(state.unit), distance(figure.businessTenths)),
        stringResource(distanceRes(state.unit), distance(figure.personalTenths)),
    )
}

/** A quieter line: what the screen is waiting for. */
@Composable
private fun Quiet(text: String) {
    Row(horizontalArrangement = Arrangement.Start) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
