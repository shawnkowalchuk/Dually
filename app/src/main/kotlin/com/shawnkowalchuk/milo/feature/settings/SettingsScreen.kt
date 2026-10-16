package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.TripSound
import java.time.DayOfWeek

/** What the tiles of the Settings screen can ask for. */
internal class SettingsActions(
    val onChangeTruck: () -> Unit,
    val onGraceStep: (longer: Boolean) -> Unit,
    val onParkedLimitStep: (longer: Boolean) -> Unit,
    val onMinimumDistanceStep: (longer: Boolean) -> Unit,
    val onDistanceAdjustStep: (up: Boolean) -> Unit,
    val onDistanceUnit: (DistanceUnit) -> Unit,
    val onSoundEnabled: (TripSound, Boolean) -> Unit,
    val onOpenSounds: (TripSound) -> Unit,
    val onDrivingAlertEnabled: (Boolean) -> Unit,
    val schedule: ScheduleActions,
    val report: ReportDetailActions,
    val reminder: ReminderActions,
)

/** What the tile of the report for the accountant can ask for: each field reports its text. */
internal class ReportDetailActions(
    val onName: (String) -> Unit,
    val onCompany: (String) -> Unit,
    val onVehicle: (String) -> Unit,
    val onAccountantEmail: (String) -> Unit,
)

/**
 * What the two tiles of the work schedule can ask for.
 *
 * @param onDayStart and [onDayEnd] carry a new time from a slider or from the time picker: an
 * hour from 0 to 23 and a minute, for one day, or for every day at once if the day is null.
 */
internal class ScheduleActions(
    val onDayTracked: (DayOfWeek, Boolean) -> Unit,
    val onDayStart: (DayOfWeek?, hour: Int, minute: Int) -> Unit,
    val onDayEnd: (DayOfWeek?, hour: Int, minute: Int) -> Unit,
    val onCopyHours: (DayOfWeek) -> Unit,
    val onIgnoreOutside: (Boolean) -> Unit,
)

/**
 * The settings, laid out as the owner's design draws them: which truck, beside the driving
 * alert; who the report for the accountant is from and where it goes; the monthly reminder to
 * send last month's report; how long a trip waits for the truck to reconnect, how long the
 * truck may stand still before a trip ends and how short a trip may be; whether distances are
 * shown in kilometres or in miles; the work schedule that
 * makes a trip Business or Personal; what becomes of a trip outside it; the daily check that a
 * work day has a trip; the connect sound and the trip-start sound, as the two rows of one tile
 * (since 2026-10-13); and, last, Android's backup with the export and import of all data.
 *
 * Since 2026-10-07 it is a screen of the bottom bar, in Setup's place, and Setup is the tile at
 * its top ([setupTile]).
 *
 * @param dataViewModel the tile of backup, export and import has a ViewModel of its own: see
 * [DataViewModel].
 * @param pointsViewModel the GPS points tile, under it, has one of its own too.
 * @param checkViewModel the daily check's tile has one of its own too.
 * @param odometerViewModel and so has the odometer's.
 * @param widgetViewModel and the home-screen widget's.
 * @param onChangeTruck opens the truck pairing screen. Navigation belongs to the app, not the
 * feature.
 * @param onOpenSounds opens the screen of one of the two sounds, where its sound is chosen
 * (`SoundListScreen`, since 2026-10-13).
 * @param onBack leaves the screen, when it was opened from another one (the Report screen's
 * "Open Settings"). Null when it is the bottom bar's: the bar is how it is left then.
 * @param setupTile the tile that opens the Setup checklist. The app hands it in, because it is
 * the Setup feature's: a feature never imports another one.
 * @param versionTile the version on this phone, which opens the What's new screen: the last
 * tile, handed in for the same reason (since 2026-10-08).
 * @param showCoffee false in the copy installed from Google Play, which shows no Buy me a coffee
 * tile: Google Play's payments policy does not allow a link that pays the developer outside it
 * (2026-10-09, ADR-004).
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    dataViewModel: DataViewModel,
    pointsViewModel: GpsPointsViewModel,
    checkViewModel: NothingRecordedViewModel,
    odometerViewModel: OdometerViewModel,
    widgetViewModel: HomeWidgetViewModel,
    onChangeTruck: () -> Unit,
    onOpenSounds: (TripSound) -> Unit,
    onBack: (() -> Unit)?,
    setupTile: @Composable () -> Unit,
    versionTile: @Composable () -> Unit,
    showCoffee: Boolean,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    val actions =
        SettingsActions(
            onChangeTruck = onChangeTruck,
            onGraceStep = viewModel::onGraceStep,
            onParkedLimitStep = viewModel::onParkedLimitStep,
            onMinimumDistanceStep = viewModel::onMinimumDistanceStep,
            onDistanceAdjustStep = viewModel::onDistanceAdjustStep,
            onDistanceUnit = viewModel::onDistanceUnit,
            onSoundEnabled = viewModel::onSoundEnabled,
            onOpenSounds = onOpenSounds,
            onDrivingAlertEnabled = viewModel::onDrivingAlertEnabled,
            schedule =
                ScheduleActions(
                    onDayTracked = viewModel::onDayTracked,
                    onDayStart = viewModel::onDayStart,
                    onDayEnd = viewModel::onDayEnd,
                    onCopyHours = viewModel::onCopyHours,
                    onIgnoreOutside = viewModel::onIgnoreOutsideSchedule,
                ),
            report =
                ReportDetailActions(
                    onName = viewModel::onReportName,
                    onCompany = viewModel::onReportCompany,
                    onVehicle = viewModel::onReportVehicle,
                    onAccountantEmail = viewModel::onAccountantEmail,
                ),
            reminder =
                ReminderActions(
                    onEnabled = viewModel::onReminderEnabled,
                    onDayStep = viewModel::onReminderDayStep,
                ),
        )
    SettingsContent(
        state = state,
        actions = actions,
        onBack = onBack,
        modifier = modifier,
        setupTile = setupTile,
        checkTile = { NothingRecordedTile(checkViewModel) },
        odometerTile = { OdometerTile(odometerViewModel) },
        widgetTile = { HomeWidgetTile(widgetViewModel) },
        rateRows = { RateRows(widgetViewModel) },
        dataTile = {
            DataTile(dataViewModel)
            GpsPointsTile(pointsViewModel)
        },
        versionTile = versionTile,
        showCoffee = showCoffee,
        onOpenLog = onOpenLog,
    )
}

/**
 * The screen's tiles, top to bottom in the design's order. The tiles the design does not draw
 * stand where they belong: Setup first, because it says whether a trip can start at all; the
 * daily check under the work schedule it goes by; and the tile for backup, export and import
 * last.
 *
 * @param setupTile the tile that opens Setup. It is shown whatever the settings file says:
 * the checklist does not come from it.
 * @param checkTile the tile of the daily check, handed in whole because it has a state of its
 * own.
 * @param odometerTile the truck's odometer, under the truck's tile, handed in whole for the
 * same reason.
 * @param widgetTile the home-screen widget's switch, after the sounds, handed in whole for the
 * same reason.
 * @param dataTile the tile for backup, export and import. It is handed in whole, because it
 * has a state of its own: it is shown also when the settings cannot be read, which is when a
 * copy of the trips is wanted most.
 * @param versionTile the version on this phone, under everything, as GopherForms has it. Shown
 * whatever the settings file says: the version does not come from it.
 * @param showCoffee whether the Buy me a coffee tile stands above it: not in the copy from
 * Google Play ([SettingsScreen]).
 * @param rateRows the rate the Business kilometres are priced at, in the tile of the report's
 * details since 2026-10-16. Its state is the widget's.
 * @param onOpenLog opens the Log, from its tile under Support (since 2026-10-16), or null for none.
 */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    setupTile: @Composable () -> Unit,
    checkTile: @Composable () -> Unit,
    odometerTile: @Composable () -> Unit,
    widgetTile: @Composable () -> Unit,
    dataTile: @Composable () -> Unit,
    versionTile: @Composable () -> Unit,
    showCoffee: Boolean = true,
    rateRows: @Composable () -> Unit = {},
    onOpenLog: (() -> Unit)? = null,
) {
    val spacing = MiloTheme.spacing
    TileColumn(
        modifier =
            modifier
                .fillMaxSize()
                // Large font settings or a small window must scroll rather than cut content off.
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.small),
    ) {
        AppHeader(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
            // As on Home: with the gap between two tiles, the design's 16 under the top line.
            modifier = Modifier.padding(bottom = spacing.extraSmall),
        )
        setupTile()
        when (state) {
            SettingsUiState.Reading -> NoteTile { Note(stringResource(R.string.settings_reading)) }

            SettingsUiState.Unreadable ->
                NoteTile {
                    StatusRow(
                        label = stringResource(R.string.settings_unreadable),
                        status = RowStatus.PROBLEM,
                    )
                }

            is SettingsUiState.Ready -> {
                if (state.problem == SettingsProblem.COULD_NOT_SAVE) {
                    NoteTile {
                        StatusRow(
                            label = stringResource(R.string.settings_could_not_save),
                            status = RowStatus.PROBLEM,
                        )
                    }
                }
                TruckAndAlertTiles(state, actions)
                // Under the truck: it is the truck's.
                odometerTile()
                // Second, and not last: the Report screen sends Shawn here for his name and
                // the accountant's address.
                ReportDetailsTile(state.report, actions.report, rateRows)
                // Under the report's own tile: it is the reminder to send that report.
                ReminderTile(state, actions.reminder)
                TripRulesTile(state, actions)
                // Under the trips' tile: it says how every distance is written, that tile's
                // shortest trip included.
                UnitsTile(state.distanceUnit, actions.onDistanceUnit)
                ScheduleTile(state, actions.schedule)
                OutsideHoursTile(state, actions.schedule)
                // Under the schedule: the check goes by its work days and their start.
                checkTile()
                SoundsTile(state, actions)
                widgetTile()
            }
        }
        // Last: it is used a few times a year, and it is the one tile that can replace
        // everything, so it is not among the settings that are changed in passing.
        if (state != SettingsUiState.Reading) dataTile()
        // Beside the version: none of them is a setting. Support first: it is the one a
        // person with a problem is looking for, and the Log is what it asks for.
        SupportTile()
        onOpenLog?.let { LogLinkTile(it) }
        if (showCoffee) CoffeeTile()
        versionTile()
    }
}

/** A tile for one thing the screen has to say in place of, or above, its settings. */
@Composable
private fun NoteTile(content: @Composable () -> Unit) {
    Tile(modifier = Modifier.fillMaxWidth(), padding = TilePadding.EVEN) { content() }
}
