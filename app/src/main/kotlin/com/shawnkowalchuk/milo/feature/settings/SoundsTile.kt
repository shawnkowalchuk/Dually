package com.shawnkowalchuk.milo.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.LinkSwitchRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.settings.TripSound

/**
 * "Sounds": the connect sound and the trip-start sound as the two rows of one tile, in the
 * order they play in (since 2026-10-13; Shawn: "i would like to have the audio section more
 * compact. not the entire list of sounds under each connect sound and trip start sound", and of
 * three layouts drawn for him he chose "one tile, two rows; tapping a row opens the list on its
 * own screen"). Until then each sound had a tile of its own, with the whole list in it.
 *
 * Each row names its sound, and under the name the sound in use. **The row is two things to
 * press** (`LinkSwitchRow`): its words open that sound's own screen (`SoundListScreen`), where
 * the sound is chosen, played, added and removed; and the switch at its end is pressed by
 * itself, and does what it always did. Switched off, that sound never plays; which sound is
 * chosen is kept, and still named.
 *
 * What the two tiles said and this one does not is on each sound's own screen: when the sound
 * plays, and that both play like an alarm.
 */
@Composable
internal fun SoundsTile(state: SettingsUiState.Ready, actions: SettingsActions) {
    // "Chirp · Chime": the sound in use for each, or Off.
    val off = stringResource(R.string.settings_fold_off)
    val summary =
        TripSound.entries.map { which ->
            val sound = state.sound(which)
            if (sound.enabled) sound.inUseName() else off
        }.joinToString(" · ")
    SettingsFold(
        title = stringResource(R.string.settings_sounds_title),
        summary = summary,
        // What a row asks for: each row's two targets reach 6 dp past what is drawn.
        gap = MiloTheme.spacing.rowGap,
    ) {
        for (which in TripSound.entries) {
            val sound = state.sound(which)
            LinkSwitchRow(
                title = stringResource(which.rowName),
                line = sound.inUseName(),
                link = TilePress(stringResource(which.chooseLabel)) { actions.onOpenSounds(which) },
                checked = sound.enabled,
                onCheckedChange = { actions.onSoundEnabled(which, it) },
                switchName = stringResource(which.title),
            )
        }
    }
}

/** What the row is called: short, under a tile that is called "Sounds". */
@get:StringRes
private val TripSound.rowName: Int
    get() = when (this) {
        TripSound.CONNECT -> R.string.settings_sounds_connect
        TripSound.DRIVING_OFF -> R.string.settings_sounds_trip_start
    }

/** What a screen reader says a press on the row's words does. */
@get:StringRes
private val TripSound.chooseLabel: Int
    get() = when (this) {
        TripSound.CONNECT -> R.string.settings_sounds_choose_connect
        TripSound.DRIVING_OFF -> R.string.settings_sounds_choose_trip_start
    }
