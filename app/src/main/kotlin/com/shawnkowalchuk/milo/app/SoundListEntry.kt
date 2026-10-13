package com.shawnkowalchuk.milo.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.shawnkowalchuk.milo.feature.settings.SoundListScreen
import com.shawnkowalchuk.milo.feature.settings.SoundListViewModel

/**
 * The screen of one of the two sounds (2026-10-13), for [MiloNavigation], which is at its size
 * limit (ENGINEERING_STANDARDS section 3).
 *
 * Its Play buttons use the container's one player for the Settings screens (`soundPreview`),
 * which cuts a sound off for the next one; the ViewModel stops it when the screen is left.
 */
@Composable
internal fun SoundListEntry(
    container: AppContainer,
    backStack: NavBackStack<NavKey>,
    key: SoundListKey,
) {
    SoundListScreen(
        viewModel =
            viewModel(
                factory = viewModelFactory {
                    initializer {
                        SoundListViewModel(
                            which = key.which,
                            settings = container.settingsStore,
                            ownSound = container.ownTripSound,
                            playSound = container.soundPreview::play,
                            stopSound = container.soundPreview::stop,
                            eventLog = container.eventLogRepository,
                            clock = container.clock,
                        )
                    }
                },
            ),
        onBack = { backStack.closeIfOnTop(key) },
    )
}
