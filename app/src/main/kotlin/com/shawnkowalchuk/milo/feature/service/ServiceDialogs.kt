package com.shawnkowalchuk.milo.feature.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ChoiceButton
import com.shawnkowalchuk.milo.core.designsystem.component.DateDialog
import com.shawnkowalchuk.milo.core.designsystem.component.RowButton
import com.shawnkowalchuk.milo.core.designsystem.component.TextEntry
import com.shawnkowalchuk.milo.core.designsystem.component.ValueButton
import com.shawnkowalchuk.milo.core.designsystem.text.unitNameRes
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.odometer.parseOdometer
import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import com.shawnkowalchuk.milo.core.util.localDateOf
import java.time.LocalDate
import java.time.ZoneId

/** The longest name an item may have, and the most digits of an interval. */
private const val MAX_NAME_LENGTH = 40
private const val MAX_INTERVAL_LENGTH = 6
private const val MAX_ODOMETER_LENGTH = 9
private const val MAX_NOTE_LENGTH = 120
private const val MAX_COST_LENGTH = 12

/** The longest interval in months: ten years. */
private const val MAX_MONTHS = 120

/**
 * A ready-made item: its name, and what its interval usually is, in each unit. Shawn's list:
 * "service, oil change, maintenance"; the intervals are the common ones, and he changes them.
 */
private enum class Preset(
    val nameRes: Int,
    val everyKm: Long?,
    val everyMiles: Long?,
    val months: Int?,
) {
    OIL(R.string.service_preset_oil, 8_000, 5_000, 6),
    TIRES(R.string.service_preset_tires, 10_000, 6_000, null),
    INSPECTION(R.string.service_preset_inspection, null, null, 12),
    BRAKES(R.string.service_preset_brakes, 40_000, 25_000, null),
    AIR_FILTER(R.string.service_preset_air_filter, 25_000, 15_000, 24),
    ;

    fun every(unit: DistanceUnit): Long? = if (unit == DistanceUnit.MILES) everyMiles else everyKm
}

/**
 * Adds or changes an item: its name (a ready-made one or his own), how far and how long between
 * two services, and when it was last done and at what odometer; a new one is last done today, at
 * the odometer as it is. Save stays off until there is a name and at least one interval.
 *
 * @param item the item to change, or null for a new one for [vehicle].
 * @param odometerKm the vehicle's odometer now, in kilometres, or null with no reading.
 */
@Composable
internal fun ItemDialog(
    item: ServiceItem?,
    vehicle: String?,
    odometerKm: Long?,
    unit: DistanceUnit,
    nowMs: Long,
    zone: ZoneId,
    onSave: (ServiceItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val today = localDateOf(nowMs, zone)
    var name by rememberSaveable { mutableStateOf(item?.name.orEmpty()) }
    var every by rememberSaveable {
        mutableStateOf(item?.everyKm?.let { kmToShown(it.toLong(), unit).toString() }.orEmpty())
    }
    var months by rememberSaveable { mutableStateOf(item?.everyMonths?.toString().orEmpty()) }
    var lastDay by rememberSaveable {
        mutableStateOf(localDateOf(item?.lastDoneAtMs ?: nowMs, zone))
    }
    val lastKm = item?.lastDoneKm ?: odometerKm
    var lastOdometer by rememberSaveable {
        mutableStateOf(lastKm?.let { kmToShown(it, unit).toString() }.orEmpty())
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    // Counts the ready-made names pressed, each of which fills the fields again.
    var filled by rememberSaveable { mutableIntStateOf(0) }
    val everyShown = every.trim().toLongOrNull()?.takeIf { it > 0 }
    val monthsTyped = months.trim().toIntOrNull()?.takeIf { it in 1..MAX_MONTHS }
    val odometerTyped = parseOdometer(lastOdometer)
    val fieldsRight =
        name.isNotBlank() &&
            (everyShown != null || monthsTyped != null) &&
            (every.isBlank() || everyShown != null) &&
            (months.isBlank() || monthsTyped != null) &&
            (lastOdometer.isBlank() || odometerTyped != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (item ==
                        null
                    ) {
                        R.string.service_item_new
                    } else {
                        R.string.service_item_edit
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
            ) {
                if (item == null) {
                    PresetButtons(chosen = name) { preset, label ->
                        name = label
                        every = preset.every(unit)?.toString().orEmpty()
                        months = preset.months?.toString().orEmpty()
                        filled++
                    }
                }
                key(filled) {
                    TextEntry(
                        label = stringResource(R.string.service_item_name),
                        initialText = name,
                        onTextChange = { name = it },
                        maxLength = MAX_NAME_LENGTH,
                    )
                    Text(
                        text = stringResource(R.string.service_item_either),
                        style = MiloTheme.textStyles.sentence,
                    )
                    TextEntry(
                        label = stringResource(
                            R.string.service_item_every_distance,
                            stringResource(unitNameRes(unit)),
                        ),
                        initialText = every,
                        onTextChange = { every = it },
                        maxLength = MAX_INTERVAL_LENGTH,
                        decimalNumber = true,
                        figure = true,
                    )
                    TextEntry(
                        label = stringResource(R.string.service_item_every_months),
                        initialText = months,
                        onTextChange = { months = it },
                        maxLength = MAX_INTERVAL_LENGTH,
                        decimalNumber = true,
                        figure = true,
                    )
                }
                ValueButton(
                    value = formatMediumDay(lastDay, locale),
                    onClick = { picking = true },
                    label = stringResource(R.string.service_item_last_done),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextEntry(
                    label = stringResource(
                        R.string.service_item_last_odometer,
                        stringResource(unitNameRes(unit)),
                    ),
                    initialText = lastOdometer,
                    onTextChange = { lastOdometer = it },
                    maxLength = MAX_ODOMETER_LENGTH,
                    decimalNumber = true,
                    lastField = true,
                    figure = true,
                )
            }
        },
        confirmButton = {
            RowButton(
                text = stringResource(R.string.service_save),
                enabled = fieldsRight,
                onClick = {
                    val sameDay = item != null && localDateOf(item.lastDoneAtMs, zone) == lastDay
                    onSave(
                        ServiceItem(
                            id = item?.id ?: 0,
                            vehicle = item?.vehicle ?: vehicle,
                            name = name.trim(),
                            everyKm = everyShown?.let { shownToKm(it, unit).toInt() },
                            everyMonths = monthsTyped,
                            lastDoneAtMs = if (sameDay) {
                                item.lastDoneAtMs
                            } else {
                                msOf(
                                    lastDay,
                                    today,
                                    nowMs,
                                    zone,
                                )
                            },
                            lastDoneKm = odometerTyped?.let { shownToKm(it, unit) },
                        ),
                    )
                },
            )
        },
        dismissButton = {
            RowButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
        textContentColor = MaterialTheme.colorScheme.onSurface,
    )
    if (picking) {
        DateDialog(
            date = lastDay,
            latest = today,
            confirmLabel = stringResource(R.string.action_ok),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                lastDay = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * The ready-made names, two to a line, as choice buttons: the design's chips are the colour of
 * the dialog, and would not be seen on it (as in the Trips screen's label dialog).
 */
@Composable
private fun PresetButtons(chosen: String, onChoose: (Preset, String) -> Unit) {
    val presets = Preset.entries.map { it to stringResource(it.nameRes) }
    Column(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
    ) {
        for (line in presets.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap)) {
                for ((preset, label) in line) {
                    ChoiceButton(
                        text = label,
                        selected = chosen == label,
                        onSelect = { onChoose(preset, label) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // An odd one out stays half as wide as the others.
                if (line.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The moment kept for a day picked: now for today, the start of any other day. */
private fun msOf(day: LocalDate, today: LocalDate, nowMs: Long, zone: ZoneId): Long =
    if (day == today) nowMs else day.atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * "Done" for an item: today, at the odometer as it is (which he can change), with a note and a
 * cost if he likes. The next service is counted from here.
 *
 * @param odometerKm the vehicle's odometer now, in kilometres, or null with no reading.
 */
@Composable
internal fun DoneDialog(
    name: String,
    odometerKm: Long?,
    unit: DistanceUnit,
    onDone: (odometerKm: Long?, note: String?, costCents: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var odometer by rememberSaveable {
        mutableStateOf(odometerKm?.let { kmToShown(it, unit).toString() }.orEmpty())
    }
    var note by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    val odometerTyped = parseOdometer(odometer)
    val costTyped = parseCost(cost)
    val odometerWrong = odometer.isNotBlank() && odometerTyped == null
    val costWrong = cost.isNotBlank() && costTyped == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.service_done_title, name),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
            ) {
                Text(
                    text = stringResource(R.string.service_done_text),
                    style = MiloTheme.textStyles.sentence,
                )
                TextEntry(
                    label = stringResource(
                        R.string.service_done_odometer,
                        stringResource(unitNameRes(unit)),
                    ),
                    initialText = odometer,
                    onTextChange = { odometer = it },
                    maxLength = MAX_ODOMETER_LENGTH,
                    decimalNumber = true,
                    figure = true,
                    error = stringResource(R.string.service_odometer_wrong).takeIf {
                        odometerWrong
                    },
                )
                TextEntry(
                    label = stringResource(R.string.service_done_cost),
                    initialText = cost,
                    onTextChange = { cost = it },
                    maxLength = MAX_COST_LENGTH,
                    decimalNumber = true,
                    figure = true,
                    error = stringResource(R.string.service_cost_wrong).takeIf { costWrong },
                )
                TextEntry(
                    label = stringResource(R.string.service_done_note),
                    initialText = note,
                    onTextChange = { note = it },
                    maxLength = MAX_NOTE_LENGTH,
                    lastField = true,
                )
            }
        },
        confirmButton = {
            RowButton(
                text = stringResource(R.string.service_done),
                enabled = !odometerWrong && !costWrong,
                onClick = {
                    onDone(odometerTyped?.let { shownToKm(it, unit) }, note.trim(), costTyped)
                },
            )
        },
        dismissButton = {
            RowButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
        textContentColor = MaterialTheme.colorScheme.onSurface,
    )
}
