package com.shawnkowalchuk.milo.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.PinnedHeaderColumn
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TilePair
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.AppLook
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
    val onLook: (AppLook) -> Unit,
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
 * @param cloudViewModel and the optional cloud backup's (ADR-012), shown above the tile for
 * backup, export and import.
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
 * row: Google Play's payments policy does not allow a link that pays the developer outside it
 * (2026-10-09, ADR-004).
 * @param page the page of Settings to show (since 2026-11-12), or null for the main screen.
 * @param onOpenPage opens a page on top of the main screen.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    dataViewModel: DataViewModel,
    pointsViewModel: GpsPointsViewModel,
    checkViewModel: NothingRecordedViewModel,
    odometerViewModel: OdometerViewModel,
    widgetViewModel: HomeWidgetViewModel,
    cloudViewModel: CloudBackupViewModel<Context>,
    onChangeTruck: () -> Unit,
    onOpenSounds: (TripSound) -> Unit,
    onBack: (() -> Unit)?,
    setupTile: @Composable () -> Unit,
    versionTile: @Composable () -> Unit,
    showCoffee: Boolean,
    modifier: Modifier = Modifier,
    page: SettingsPage? = null,
    onOpenPage: (SettingsPage) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val widget by widgetViewModel.state.collectAsState()

    val actions =
        SettingsActions(
            onChangeTruck = onChangeTruck,
            onGraceStep = viewModel::onGraceStep,
            onParkedLimitStep = viewModel::onParkedLimitStep,
            onMinimumDistanceStep = viewModel::onMinimumDistanceStep,
            onDistanceAdjustStep = viewModel::onDistanceAdjustStep,
            onDistanceUnit = viewModel::onDistanceUnit,
            onLook = viewModel::onLook,
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
        backupTile = { CloudBackupTile(cloudViewModel) },
        dataTile = {
            DataTile(dataViewModel)
            GpsPointsTile(pointsViewModel)
        },
        versionTile = versionTile,
        showCoffee = showCoffee,
        page = page,
        onOpenPage = onOpenPage,
        widgetLine =
            widget?.let {
                val on = it.enabled
                stringResource(
                    if (on) R.string.settings_page_widget_on else R.string.settings_fold_off,
                )
            },
    )
}

/**
 * The screen's tiles, top to bottom. Since 2026-11-12 (Shawn: "it is to busy. to many drawers",
 * then "do all of it" on the mock-up) the main screen has no drawers: Setup; the settings that
 * are changed in passing (Units and Look, the truck and the driving alert, the odometer); one
 * tile for each [SettingsPage], which says what the page is set to and opens it; and a short
 * list at the end. A page holds the tiles the main screen had for it, drawn open
 * ([LocalSettingsOnPage]).
 *
 * @param page the page to show, or null for the main screen.
 * @param onOpenPage opens one of the pages on top of this screen.
 * @param setupTile the tile that opens Setup, on the main screen. It is shown whatever the
 * settings file says: the checklist does not come from it. Since 2026-11-12 the Log is opened
 * from the Setup screen, not from here.
 * @param checkTile the tile of the daily check, on the Work hours page, handed in whole because
 * it has a state of its own.
 * @param odometerTile the truck's odometer, under the truck's tile, handed in whole for the
 * same reason.
 * @param widgetTile the home-screen widget's tile, the Widget page's, handed in whole too.
 * @param widgetLine what the Widget tile of the main screen says the widget is set to.
 * @param dataTile the tiles for backup, export, import and the GPS points: the Your data page's.
 * They are shown also when the settings cannot be read, which is when a copy of the trips is
 * wanted most.
 * @param versionTile the version on this phone, under everything, as GopherForms has it. Shown
 * whatever the settings file says: the version does not come from it.
 * @param backupTile the Online backup tile, under Units and Look on the main screen (since
 * 2026-11-12; on the Your data page before), handed in whole because it has a state of its own.
 * @param showCoffee whether the list at the end has Buy me a coffee: not in the copy from
 * Google Play ([SettingsScreen]).
 * @param rateRows the rate the Business kilometres are priced at, in the tile of the report's
 * details since 2026-10-16. Its state is the widget's.
 */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    page: SettingsPage? = null,
    onOpenPage: (SettingsPage) -> Unit = {},
    setupTile: @Composable () -> Unit,
    checkTile: @Composable () -> Unit,
    odometerTile: @Composable () -> Unit,
    widgetTile: @Composable () -> Unit,
    dataTile: @Composable () -> Unit,
    versionTile: @Composable () -> Unit,
    backupTile: @Composable () -> Unit = {},
    showCoffee: Boolean = true,
    rateRows: @Composable () -> Unit = {},
    widgetLine: String? = null,
) {
    PinnedHeaderColumn(
        header = {
            AppHeader(
                title = stringResource(page?.titleRes ?: R.string.settings_title),
                onBack = onBack,
            )
        },
        modifier = modifier.fillMaxSize(),
    ) {
        if (page == null) setupTile()
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
                if (page == null) {
                    MainSettings(state, actions, odometerTile, backupTile, onOpenPage, widgetLine)
                } else {
                    CompositionLocalProvider(LocalSettingsOnPage provides true) {
                        when (page) {
                            SettingsPage.WORK_HOURS -> {
                                ScheduleTile(state, actions.schedule)
                                OutsideHoursTile(state, actions.schedule)
                                // The check goes by the work days and their start.
                                checkTile()
                            }

                            SettingsPage.REPORT -> {
                                ReportDetailsTile(state.report, actions.report, rateRows)
                                ReminderTile(state, actions.reminder)
                            }

                            SettingsPage.TRIPS -> TripRulesTile(state, actions)

                            SettingsPage.SOUNDS -> SoundsTile(state, actions)

                            SettingsPage.WIDGET -> widgetTile()

                            SettingsPage.DATA -> Unit
                        }
                    }
                }
            }
        }
        if (page == SettingsPage.DATA && state != SettingsUiState.Reading) {
            CompositionLocalProvider(LocalSettingsOnPage provides true) { dataTile() }
        }
        if (page == null) {
            // Last on the main screen: it is used a few times a year, and its page holds the
            // one tile that can replace everything.
            if (state != SettingsUiState.Reading) {
                SettingsLinkTile(
                    title = stringResource(R.string.settings_page_data),
                    line = stringResource(R.string.settings_page_data_line),
                    onOpen = { onOpenPage(SettingsPage.DATA) },
                )
            }
            // None of them is a setting. Support first: it is the one a person with a problem
            // is looking for.
            FooterLinks(
                buildList {
                    add { SupportRow() }
                    add { ReviewRow() }
                    if (showCoffee) add { CoffeeRow() }
                },
            )
            versionTile()
        }
    }
}

/**
 * The main screen's settings, under Setup: the ones changed in passing, drawn whole, and one
 * tile for each page that sums it up.
 */
@Composable
private fun MainSettings(
    state: SettingsUiState.Ready,
    actions: SettingsActions,
    odometerTile: @Composable () -> Unit,
    backupTile: @Composable () -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    widgetLine: String?,
) {
    // First of the settings (Shawn, 2026-11-12: "can you move the units and light/darkmode to
    // above truck and driving aler"): how everything is shown.
    UnitsAndLookTiles(state.distanceUnit, state.look, actions.onDistanceUnit, actions.onLook)
    // The optional online backup, out in the open on the first screen of Settings (Shawn,
    // 2026-11-12: "the backup shouldnt be hidden").
    backupTile()
    TruckAndAlertTiles(state, actions)
    // Under the truck: it is the truck's.
    odometerTile()
    val days = state.schedule.count { it.tracked }
    SettingsLinkTile(
        title = stringResource(R.string.settings_page_work_hours),
        line = weekSummary(days, shownWeeklyMinutes(state.schedule, null)),
        second =
            stringResource(
                if (state.ignoreOutsideSchedule) {
                    R.string.settings_page_work_hours_ignore
                } else {
                    R.string.settings_page_work_hours_personal
                },
            ),
        onOpen = { onOpenPage(SettingsPage.WORK_HOURS) },
    )
    val report = state.report
    SettingsLinkTile(
        title = stringResource(R.string.settings_page_report),
        line =
            report.accountantEmail.ifBlank {
                stringResource(R.string.settings_report_summary_missing)
            },
        second =
            if (state.reminderEnabled) {
                stringResource(reminderLineRes(state.reminderDay), state.reminderDay)
            } else {
                stringResource(R.string.settings_page_report_no_reminder)
            },
        onOpen = { onOpenPage(SettingsPage.REPORT) },
    )
    SettingsLinkTile(
        title = stringResource(R.string.settings_page_trips),
        line = tripRulesSummary(state),
        onOpen = { onOpenPage(SettingsPage.TRIPS) },
    )
    val off = stringResource(R.string.settings_fold_off)
    TilePair(
        first = { half ->
            SettingsLinkTile(
                title = stringResource(R.string.settings_sounds_title),
                line =
                    TripSound.entries.map { which ->
                        val sound = state.sound(which)
                        if (sound.enabled) sound.inUseName() else off
                    }.joinToString(" · "),
                onOpen = { onOpenPage(SettingsPage.SOUNDS) },
                modifier = half,
            )
        },
        second = { half ->
            SettingsLinkTile(
                title = stringResource(R.string.settings_page_widget),
                line = widgetLine ?: off,
                onOpen = { onOpenPage(SettingsPage.WIDGET) },
                modifier = half,
            )
        },
    )
}

/** A tile for one thing the screen has to say in place of, or above, its settings. */
@Composable
private fun NoteTile(content: @Composable () -> Unit) {
    Tile(modifier = Modifier.fillMaxWidth(), padding = TilePadding.EVEN) { content() }
}
