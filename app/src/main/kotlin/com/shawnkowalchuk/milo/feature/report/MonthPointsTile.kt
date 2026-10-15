package com.shawnkowalchuk.milo.feature.report

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButton
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.ActionKind
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatMonthName

/**
 * The tile that offers to delete a sent month's GPS points, under the Business and Status
 * tiles: what the points are, how much room they take, and that every trip keeps its figures.
 * Its buttons are those the design draws under a trip: "Delete" in the design's red, as on a
 * trip, and "Keep" quiet. The tile is the question itself, so nothing asks again.
 */
@Composable
internal fun MonthPointsTile(state: MonthPointsState, onDelete: () -> Unit, onKeep: () -> Unit) {
    if (state == MonthPointsState.Hidden) return
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    Tile(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap)) {
            when (state) {
                MonthPointsState.Hidden -> Unit

                is MonthPointsState.Offered -> {
                    val month = formatMonthName(state.month, locale)
                    val size = Formatter.formatShortFileSize(context, state.held.bytes)
                    val trips = state.held.tripIds.size
                    Text(
                        text = stringResource(R.string.report_points_title, month),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.report_points_offer,
                                trips,
                                month,
                                trips,
                                size,
                            ),
                        style = MiloTheme.textStyles.sentence,
                    )
                    if (state.failed) {
                        StatusRow(
                            label = stringResource(R.string.report_points_failed),
                            status = RowStatus.PROBLEM,
                        )
                    }
                    ActionButtonRow(
                        actions =
                            listOf(
                                ActionButton(
                                    label = stringResource(R.string.report_points_delete),
                                    kind = ActionKind.DANGER,
                                    onClick = onDelete,
                                ),
                                ActionButton(
                                    label = stringResource(R.string.report_points_keep),
                                    kind = ActionKind.PLAIN,
                                    onClick = onKeep,
                                ),
                            ),
                    )
                }

                is MonthPointsState.Deleting ->
                    Quiet(stringResource(R.string.report_points_deleting))

                is MonthPointsState.Deleted -> {
                    val size = Formatter.formatShortFileSize(context, state.bytes)
                    StatusRow(
                        label = stringResource(R.string.report_points_deleted, size),
                        status = RowStatus.OK,
                    )
                }
            }
        }
    }
}
