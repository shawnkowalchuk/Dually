package com.shawnkowalchuk.milo.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.trip.TripCorrections
import com.shawnkowalchuk.milo.feature.tripgraph.TripGraphScreen
import com.shawnkowalchuk.milo.feature.tripgraph.TripGraphViewModel
import com.shawnkowalchuk.milo.feature.trips.TripLabelViewModel
import com.shawnkowalchuk.milo.feature.trips.TripsScreen
import com.shawnkowalchuk.milo.feature.trips.TripsViewModel
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * The Trips screen with its two view models: the month's trips, and since 2026-10-08 the
 * choice of a trip's label. For [MiloNavigation], which is at its size limit
 * (ENGINEERING_STANDARDS section 3).
 *
 * @param savedTripStartMs when a trip just saved on the edit screen starts, or null, and
 * [onSavedTripShown] that its month and day are on screen: MiloNavigation holds it, because
 * the edit screen sets it.
 */
@Composable
internal fun TripsEntry(
    container: AppContainer,
    backStack: NavBackStack<NavKey>,
    savedTripStartMs: Long?,
    onSavedTripShown: () -> Unit,
) {
    TripsScreen(
        viewModel =
            viewModel(
                factory = viewModelFactory {
                    initializer {
                        TripsViewModel(
                            trips = container.tripRepository,
                            corrections =
                                TripCorrections(
                                    trips = container.tripRepository,
                                    eventLog = container.eventLogRepository,
                                    clock = container.clock,
                                ),
                            tripActivity = container.tripController.activity,
                            openTripStart = container.tripAddresses.openTripStart,
                            unit = container.shownUnit.unit,
                            // The rate Home and the widget price the month at; none while the
                            // settings cannot be read.
                            centsPerKm =
                                container.settingsStore.settings
                                    .map<MiloSettings, Int?> { it.homeWidgetCentsPerKm }
                                    .catch { if (it is IOException) emit(null) else throw it },
                            sentReports = container.sentReportRepository.observeSent(),
                            lookUpAddresses = container.tripAddresses::catchUp,
                            clock = container.clock,
                            zone = ZoneId::systemDefault,
                        )
                    }
                },
            ),
        labels =
            viewModel(
                factory = viewModelFactory {
                    initializer {
                        TripLabelViewModel(
                            trips = container.tripRepository,
                            eventLog = container.eventLogRepository,
                            clock = container.clock,
                        )
                    }
                },
            ),
        savedTripStartMs = savedTripStartMs,
        onSavedTripShown = onSavedTripShown,
        onEditTrip = { tripId -> backStack.openOnTop(TripEditKey(tripId)) },
        onAddTrip = { backStack.openOnTop(TripEditKey(tripId = null)) },
        onOpenReport = { month -> backStack.openOnTop(ReportKey(month.year, month.monthValue)) },
        onOpenGraph = { backStack.openOnTop(TripGraphKey) },
    )
}

/** The Trips screen's graph, opened on top of Trips: Back leads back to it (since 2026-10-16). */
@Composable
internal fun TripGraphEntry(container: AppContainer, backStack: NavBackStack<NavKey>) {
    TripGraphScreen(
        viewModel =
            viewModel(
                factory = viewModelFactory {
                    initializer {
                        TripGraphViewModel(
                            trips = container.tripRepository,
                            unit = container.shownUnit.unit,
                            clock = container.clock,
                            zone = ZoneId::systemDefault,
                        )
                    }
                },
            ),
        onBack = { backStack.closeIfOnTop(TripGraphKey) },
    )
}
