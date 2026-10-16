package com.shawnkowalchuk.milo.feature.service

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButton
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.ActionKind
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.component.GroupLabel
import com.shawnkowalchuk.milo.core.designsystem.component.LocalMiloClock
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileButton
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.service.DueState
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.time.ZoneId

/**
 * The Service screen (since 2026-10-16), the fourth button of the bottom bar where the Log was:
 * each vehicle's items, when each is due, and what was done. A press on an item brings up Done,
 * Edit and Delete and its history, as a trip's row brings up its buttons on Trips.
 */
@Composable
fun ServiceScreen(viewModel: ServiceViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    // Which item is open, which is being edited (0 for a new one), done or deleted. Saved, so
    // that turning the phone keeps them.
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by rememberSaveable { mutableStateOf<Pair<Long, String?>?>(null) }
    var doneId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val spacing = MiloTheme.spacing
    TileColumn(
        modifier =
            modifier.fillMaxSize().verticalScroll(
                rememberScrollState(),
            ).padding(vertical = spacing.small),
    ) {
        AppHeader(
            title = stringResource(R.string.service_title),
            modifier = Modifier.padding(bottom = spacing.extraSmall),
        )
        val vehicles = state.vehicles
        if (vehicles == null) {
            Tile(modifier = Modifier.fillMaxWidth()) {
                Quiet(stringResource(R.string.service_reading))
            }
            return@TileColumn
        }
        for (vehicle in vehicles) {
            vehicle.name?.let { GroupLabel(it) }
            for (line in vehicle.lines) {
                ItemTile(
                    line = line,
                    unit = state.unit,
                    open = line.item.id == openId,
                    onToggle = { openId = line.item.id.takeIf { it != openId } },
                    onDone = { doneId = line.item.id },
                    onEdit = { editing = line.item.id to vehicle.address },
                    onDelete = { deletingId = line.item.id },
                )
            }
            if (vehicle.lines.isEmpty()) {
                Tile(modifier = Modifier.fillMaxWidth()) {
                    Quiet(stringResource(R.string.service_none))
                }
            }
            TileButton(
                text = stringResource(R.string.service_add),
                onClick = { editing = 0L to vehicle.address },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    val all = state.vehicles.orEmpty()
    val nowMs = LocalMiloClock.current()
    val zone = ZoneId.systemDefault()
    editing?.let { (id, address) ->
        val vehicle = all.firstOrNull { it.address == address } ?: all.firstOrNull()
        ItemDialog(
            item = all.flatMap { it.lines }.firstOrNull { it.item.id == id }?.item,
            vehicle = address,
            odometerKm = vehicle?.odometerKm,
            unit = state.unit,
            nowMs = nowMs,
            zone = zone,
            onSave = { item ->
                viewModel.onSave(item)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    doneId?.let { id ->
        val vehicle = all.firstOrNull { v -> v.lines.any { it.item.id == id } }
        val line = vehicle?.lines?.firstOrNull { it.item.id == id }
        if (line == null) {
            doneId = null
            return@let
        }
        DoneDialog(
            name = line.item.name,
            odometerKm = vehicle.odometerKm,
            unit = state.unit,
            onDone = { odometerKm, note, cost ->
                viewModel.onDone(id, odometerKm, note, cost)
                doneId = null
            },
            onDismiss = { doneId = null },
        )
    }
    deletingId?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.service_delete_title),
            text = stringResource(R.string.service_delete_text),
            confirmLabel = stringResource(R.string.service_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                viewModel.onDelete(id)
                deletingId = null
            },
            onDismiss = { deletingId = null },
        )
    }
}

@Composable
private fun ItemTile(
    line: ServiceLine,
    unit: DistanceUnit,
    open: Boolean,
    onToggle: () -> Unit,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    Tile(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap),
        ) {
            Text(text = line.item.name, style = MaterialTheme.typography.titleSmall)
            StatusRow(
                label = dueWords(line.item, line.due, unit, locale),
                // Soon and due both want his eye; the words say which.
                status = if (line.due.state == DueState.OK) RowStatus.OK else RowStatus.PROBLEM,
            )
            Quiet(lastDoneWords(line.item, unit, locale, zone))
        }
        if (open) {
            ActionButtonRow(
                actions =
                    listOf(
                        ActionButton(
                            stringResource(R.string.service_done),
                            ActionKind.ACCENT,
                            onDone,
                        ),
                        ActionButton(
                            stringResource(R.string.service_edit),
                            ActionKind.PLAIN,
                            onEdit,
                        ),
                        ActionButton(
                            stringResource(R.string.service_delete),
                            ActionKind.DANGER,
                            onDelete,
                        ),
                    ),
            )
            if (line.history.isNotEmpty()) {
                TileLabel(stringResource(R.string.service_history))
                for (record in line.history) Quiet(recordWords(record, unit, locale, zone))
            }
        }
    }
}

/** A quieter line. */
@Composable
internal fun Quiet(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
