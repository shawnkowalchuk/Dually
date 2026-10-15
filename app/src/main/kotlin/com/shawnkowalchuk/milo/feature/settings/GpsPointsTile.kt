package com.shawnkowalchuk.milo.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButton
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.ActionKind
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The GPS points tile, under the one for backup, export and import: how much room the points
 * take, how much of it is of months whose report was sent, and a button that deletes those,
 * after a question. Drawn like the tile above it. Nothing is drawn until storage has been read.
 */
@Composable
internal fun GpsPointsTile(viewModel: GpsPointsViewModel) {
    val state by viewModel.state.collectAsState()
    state?.let { GpsPointsTileContent(it, viewModel::onDelete) }
}

@Composable
private fun GpsPointsTileContent(state: GpsPointsCardState, onDelete: () -> Unit) {
    val context = LocalContext.current
    // Whether the question is open. Saved, so that turning the phone does not close it.
    var asking by rememberSaveable { mutableStateOf(false) }
    val sent = state.sent
    val sentSize = Formatter.formatShortFileSize(context, sent.bytes)
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.rowGap,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap)) {
            Text(
                text = stringResource(R.string.settings_points_title),
                style = MaterialTheme.typography.bodyLarge,
                // Lets a screen reader jump from tile to tile.
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text =
                    stringResource(
                        R.string.settings_points_line,
                        Formatter.formatShortFileSize(context, state.storedBytes),
                    ),
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val months = sent.months.size
        if (sent.points > 0) {
            Note(pluralStringResource(R.plurals.settings_points_sent, months, sentSize, months))
            ActionButtonRow(
                actions =
                    listOf(
                        ActionButton(
                            label = stringResource(R.string.settings_points_delete),
                            kind = ActionKind.DANGER,
                            onClick = { asking = true },
                            enabled = !state.working,
                        ),
                    ),
            )
        } else {
            Note(stringResource(R.string.settings_points_none_sent))
        }
        if (state.working) Note(stringResource(R.string.settings_points_deleting))
        when (state.outcome) {
            null -> Unit

            GpsPointsOutcome.DELETED -> {
                val freed = Formatter.formatShortFileSize(context, state.deletedBytes)
                StatusRow(stringResource(R.string.settings_points_deleted, freed), RowStatus.OK)
            }

            GpsPointsOutcome.FAILED ->
                StatusRow(stringResource(R.string.settings_points_failed), RowStatus.PROBLEM)
        }
    }

    if (asking && sent.points > 0) {
        ConfirmDialog(
            title = stringResource(R.string.settings_points_question_title),
            text =
                pluralStringResource(
                    R.plurals.settings_points_question,
                    sent.tripIds.size,
                    sentSize,
                    sent.tripIds.size,
                ),
            confirmLabel = stringResource(R.string.settings_points_question_delete),
            dismissLabel = stringResource(R.string.settings_points_question_keep),
            onConfirm = {
                asking = false
                onDelete()
            },
            onDismiss = { asking = false },
        )
    }
}
