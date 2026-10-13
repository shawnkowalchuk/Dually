package com.shawnkowalchuk.milo.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import com.shawnkowalchuk.milo.data.settings.TripSound

/** The kind of file the picker offers for a sound. */
private const val ANY_AUDIO = "audio/*"

/** What the rows and the button of a sound's screen can ask for. */
internal class SoundListActions(
    val onChooseBuiltIn: (BuiltInSound) -> Unit,
    val onChooseOwn: (ownSoundUri: String) -> Unit,
    val onPlayBuiltIn: (BuiltInSound) -> Unit,
    val onPlayOwn: (ownSoundUri: String) -> Unit,
    val onRemove: (ownSoundUri: String) -> Unit,
    val onAdd: () -> Unit,
)

/**
 * The screen of one of the two sounds, "Connect sound" or "Trip-start sound": which sound it
 * plays. Opened from that sound's row of the "Sounds" tile on Settings (since 2026-10-13; until
 * then the list stood in a tile of Settings itself, once he had added a sound). Back leads back
 * to Settings, where it was.
 *
 * Top to bottom: a grey line on when the sound plays; "Built in", the twelve sounds MilO
 * carries, the ones made for this moment first; "Your sounds", the audio files he added from
 * the phone, the same list on both screens, with "Add a sound" under them; and a grey line that
 * says that the sounds play like an alarm.
 *
 * **A press on a row makes it the sound in use at once,** and the row is marked. Each row has a
 * Play button, which plays that row's sound whether or not it is the one in use, through a
 * player that cuts off: Play on another row starts that one. Each of his own has "Remove"
 * beside it, which takes it off the list for both sounds, without a question, as before:
 * nothing is lost that cannot be added again from the phone.
 *
 * **Leaving the screen stops what Play started:** Back, a button of the bottom bar, MilO put
 * away, or the file picker coming up over it.
 *
 * @param onBack leaves the screen. Navigation belongs to the app, not the feature.
 */
@Composable
fun SoundListScreen(
    viewModel: SoundListViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    // Android's own file picker. It answers with the file Shawn chose, or with nothing if he
    // closed it, in which case nothing changes. Which of the two sounds it was opened for is
    // this screen itself, which the back stack brings back with its sound if Android puts MilO
    // away while the picker is in front.
    val soundPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
            if (picked != null) viewModel.onOwnSoundPicked(picked.toString())
        }

    // A sound of his own can be minutes long, and it is played as an alarm.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onLeft() }

    val actions =
        SoundListActions(
            onChooseBuiltIn = viewModel::onChooseBuiltIn,
            onChooseOwn = viewModel::onChooseOwn,
            onPlayBuiltIn = viewModel::onPlayBuiltIn,
            onPlayOwn = viewModel::onPlayOwn,
            onRemove = viewModel::onRemove,
            onAdd = {
                try {
                    soundPicker.launch(arrayOf(ANY_AUDIO))
                } catch (noPicker: ActivityNotFoundException) {
                    // A phone with its file picker removed or disabled. The screen says so.
                    viewModel.onNoFilePicker()
                }
            },
        )
    SoundListContent(viewModel.which, state, actions, onBack, modifier)
}

/**
 * The screen in the language of the owner's design, which has no drawing of it, laid out as the
 * pairing screen is: the top line with the way back, a quiet sentence, and then each list as a
 * small label on the page over a tile.
 *
 * @param which the sound the screen is about, known before the settings are read: it is the
 * screen's name.
 */
@Composable
internal fun SoundListContent(
    which: TripSound,
    state: SoundListUiState,
    actions: SoundListActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiloTheme.spacing
    // In from the edge like a label above a tile.
    val onThePage = Modifier.padding(horizontal = spacing.extraSmall)
    TileColumn(
        modifier =
            modifier
                .fillMaxSize()
                // Twelve rows and his own: the lists are longer than most screens.
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.small),
    ) {
        AppHeader(
            title = stringResource(which.title),
            onBack = onBack,
            // As on Home: with the gap between two tiles, the design's 16 under the top line.
            modifier = Modifier.padding(bottom = spacing.extraSmall),
        )
        when (state) {
            SoundListUiState.Reading -> NoteTile { Note(stringResource(R.string.settings_reading)) }

            SoundListUiState.Unreadable ->
                NoteTile {
                    StatusRow(
                        label = stringResource(R.string.settings_unreadable),
                        status = RowStatus.PROBLEM,
                    )
                }

            is SoundListUiState.Ready -> {
                Note(stringResource(which.whenItPlays), onThePage)
                if (state.problem == SoundListProblem.COULD_NOT_SAVE) {
                    NoteTile { ToldInView(stringResource(R.string.settings_could_not_save)) }
                }
                // TODO(debt): opened with a sound of his own in use, the marked row is under
                //  the twelve built-in ones and out of view until the screen is scrolled. The
                //  order was decided (built in first), and the tile he came from names the
                //  sound. See docs/FINDINGS_LOG.md, 2026-10-13.
                BuiltInSounds(state, actions)
                OwnSounds(state, actions)
                // True of both sounds, and said on the screen of each.
                Note(stringResource(R.string.settings_sound_alarm_note), onThePage)
            }
        }
    }
}

/** The grey line on when the sound plays. */
@get:StringRes
private val TripSound.whenItPlays: Int
    get() = when (this) {
        TripSound.CONNECT -> R.string.settings_sound_when
        TripSound.DRIVING_OFF -> R.string.settings_trip_sound_when
    }

/** A tile for one thing the screen has to say in place of, or above, its lists. */
@Composable
private fun NoteTile(content: @Composable () -> Unit) {
    Tile(modifier = Modifier.fillMaxWidth(), padding = TilePadding.EVEN) { content() }
}
