package com.shawnkowalchuk.milo.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.shawnkowalchuk.milo.data.settings.changeServiceBook
import com.shawnkowalchuk.milo.feature.eventlog.EventLogScreen
import com.shawnkowalchuk.milo.feature.eventlog.EventLogViewModel
import com.shawnkowalchuk.milo.feature.service.ServiceScreen
import com.shawnkowalchuk.milo.feature.service.ServiceViewModel
import java.time.ZoneId

/**
 * The Service screen, a button of the bottom bar where the Log was (since 2026-10-16). For
 * [MiloNavigation], which is at its size limit (ENGINEERING_STANDARDS section 3).
 */
@Composable
internal fun ServiceEntry(container: AppContainer) {
    ServiceScreen(
        viewModel =
            viewModel(
                factory = viewModelFactory {
                    initializer {
                        ServiceViewModel(
                            settings = container.settingsStore.settings,
                            finishedTrips = container.tripRepository.observeFinishedTrips(),
                            change = { container.settingsStore.changeServiceBook(it) },
                            clock = container.clock,
                            zone = ZoneId::systemDefault,
                        )
                    }
                },
            ),
    )
}

/** The Log, opened on top of Settings since 2026-10-16: Back leads back to it. */
@Composable
internal fun LogEntry(container: AppContainer, backStack: NavBackStack<NavKey>) {
    EventLogScreen(
        viewModel =
            viewModel(
                factory = viewModelFactory {
                    initializer {
                        EventLogViewModel(
                            eventLog = container.eventLogRepository,
                            files = container.eventLogFiles,
                            shareRequest = container.reports.handOff::toShareText,
                            clock = container.clock,
                            zone = ZoneId::systemDefault,
                        )
                    }
                },
            ),
        onBack = { backStack.closeIfOnTop(LogKey) },
    )
}
