package com.shawnkowalchuk.milo.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButton
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.ActionKind
import com.shawnkowalchuk.milo.core.designsystem.component.FigureRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.text.categoryWordsRes
import com.shawnkowalchuk.milo.core.designsystem.text.distanceSpokenRes
import com.shawnkowalchuk.milo.core.designsystem.text.placesWords
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.data.trip.categoriesOffered

// The list of today's finished trips, in both of Home's layouts.

/**
 * Today's finished trips, newest first: where each went, its times, what it is saved as and
 * its kilometres. The whole tile leads to the Trips screen.
 *
 * Since 2026-10-09 it stands in both layouts (Shawn: "just have todays trips sorted latest at
 * the top"): with no trip open in place of the last trip alone, and while one is recorded as
 * before.
 *
 * @param recording true while a trip is recorded: it is not among the rows yet, and the tile
 * says so under them.
 */
@Composable
internal fun TodayListTile(
    figures: HomeTrips?,
    format: HomeFormat,
    onOpenTrips: () -> Unit,
    recording: Boolean,
    rowActions: TodayRowActions,
) {
    val rows = figures?.rows.orEmpty()
    // Which trip has its buttons up. Saved, so that turning the phone keeps it.
    var openTripId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Since 2026-10-16 a row is pressed to bring up its buttons, so the tile as a whole no
    // longer opens Trips: "Trips" in its corner does.
    Tile(modifier = Modifier.fillMaxWidth(), gap = MiloTheme.spacing.extraSmall) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = MiloTheme.spacing.buttonGap),
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        ) {
            TileLabel(
                text = todayListLabel(figures),
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                text = stringResource(R.string.nav_trips),
                modifier =
                    Modifier
                        .alignByBaseline()
                        .clickable(
                            onClickLabel = stringResource(R.string.home_open_trips),
                            role = Role.Button,
                            onClick = onOpenTrips,
                        ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        when {
            figures == null -> Sentence(stringResource(R.string.trips_reading))
            rows.isEmpty() -> Sentence(stringResource(R.string.home_today_none))
        }
        rows.forEachIndexed { index, row ->
            TodayRow(
                trip = row,
                format = format,
                open = row.tripId == openTripId,
                onToggle = { openTripId = row.tripId.takeIf { it != openTripId } },
                actions = rowActions,
            )
            if (index < rows.lastIndex) HorizontalDivider()
        }
        if (recording) {
            Text(
                text = stringResource(R.string.home_today_in_progress_note),
                modifier = Modifier.padding(top = MiloTheme.spacing.small),
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Today · 3 trips · 1 h 5 min driving", or "Today" before the first finished trip. */
@Composable
private fun todayListLabel(figures: HomeTrips?): String {
    val today = figures?.today
    if (today == null || today.count == 0) return stringResource(R.string.home_today_title)
    return pluralStringResource(
        R.plurals.home_today_list,
        today.count,
        today.count,
        durationText(today.driveTimeMs),
    )
}

/**
 * One finished trip. Its figure is the number alone, as drawn, and a screen reader is read the
 * unit too. A trip that is not Business has its figure greyed: it is not in the Business
 * figure of the tile above, and the white figures are the ones that add up to it.
 */
@Composable
private fun TodayRow(
    trip: HomeTrip,
    format: HomeFormat,
    open: Boolean,
    onToggle: () -> Unit,
    actions: TodayRowActions,
) {
    val distance = formatDistance(trip.distanceMetres, format.unit, format.locale)
    FigureRow(
        figure = distance,
        modifier =
            Modifier
                .clickable(
                    onClickLabel = stringResource(R.string.trips_row_press),
                    onClick = onToggle,
                )
                .padding(vertical = MiloTheme.spacing.tileGap),
        counted = trip.category == TripCategory.BUSINESS,
        figureSpoken = stringResource(distanceSpokenRes(format.unit), distance),
    ) {
        Text(text = placesWords(trip.places), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = trip.timeAndCategory(trip.timesText(format)),
            style = MiloTheme.textStyles.tileLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        // The buttons a finished trip has on the Trips screen (since 2026-10-16, Shawn: "have
        // them expand same as the trips screen to edit them"): the other category, and Edit.
        val marks =
            categoriesOffered(TripStatus.FINISHED, trip.category).map { category ->
                ActionButton(
                    label = stringResource(categoryWordsRes(category, ranPastSchedule = false)),
                    kind = ActionKind.PLAIN,
                    onClick = { actions.onMark(trip.tripId, category) },
                    spokenName =
                        stringResource(
                            if (category == TripCategory.BUSINESS) {
                                R.string.trips_action_mark_business
                            } else {
                                R.string.trips_action_mark_personal
                            },
                        ),
                )
            }
        val edit =
            ActionButton(
                label = stringResource(R.string.trips_button_edit),
                kind = ActionKind.ACCENT,
                onClick = { actions.onEdit(trip.tripId) },
                spokenName = stringResource(R.string.trips_action_edit),
            )
        ActionButtonRow(
            actions = marks + edit,
            modifier = Modifier.padding(bottom = MiloTheme.spacing.rowGap),
        )
    }
}

/**
 * What a row of today's trips can do once it is pressed.
 *
 * @param onMark marks the trip Business or Personal by hand, as the Trips screen does.
 * @param onEdit opens the edit screen for the trip.
 */
internal class TodayRowActions(
    val onMark: (tripId: Long, category: TripCategory) -> Unit,
    val onEdit: (tripId: Long) -> Unit,
)
