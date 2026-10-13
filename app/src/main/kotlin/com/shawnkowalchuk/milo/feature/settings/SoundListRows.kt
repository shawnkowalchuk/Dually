package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButton
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.ActionKind
import com.shawnkowalchuk.milo.core.designsystem.component.ChoiceRow
import com.shawnkowalchuk.milo.core.designsystem.component.GroupLabel
import com.shawnkowalchuk.milo.core.designsystem.component.MiloIcons
import com.shawnkowalchuk.milo.core.designsystem.component.RowButton
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.SquareIconButton
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.sound.MAX_OWN_SOUND_BYTES

// The two lists of a sound's own screen (`SoundListScreen`), each a small label on the page over
// a tile, and the row both are made of. Nothing was added to the design system for them: a row
// is the design's choice row with its square button at the end, and "Remove" is the small
// button the pairing screen has at the end of a device's row.

private const val BYTES_PER_MEGABYTE = 1024 * 1024

/** "Built in": the twelve sounds MilO carries, the ones made for this moment first. */
@Composable
internal fun BuiltInSounds(state: SoundListUiState.Ready, actions: SoundListActions) {
    GroupLabel(stringResource(R.string.sound_list_built_in))
    Tile(modifier = Modifier.fillMaxWidth(), padding = TilePadding.EVEN) {
        // No gap of the column's own: a choice row keeps its air itself.
        Column(modifier = Modifier.selectableGroup()) {
            for (row in state.builtIn) {
                SoundRow(
                    name = stringResource(row.sound.label),
                    inUse = row.inUse,
                    idle = !state.copying,
                    onChoose = { actions.onChooseBuiltIn(row.sound) },
                    onPlay = { actions.onPlayBuiltIn(row.sound) },
                )
            }
        }
    }
}

/**
 * "Your sounds": the audio files he added from the phone, in the order they were added, each
 * with its "Remove"; "Add a sound" under them; and under that what a pick came to, in sight of
 * the button that was pressed for it.
 */
@Composable
internal fun OwnSounds(state: SoundListUiState.Ready, actions: SoundListActions) {
    // While a picked file is being copied everything waits: the answer decides what the list
    // holds, and which row is in use.
    val idle = !state.copying
    GroupLabel(stringResource(R.string.sound_list_own))
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.rowGap,
    ) {
        if (state.own.isEmpty()) {
            Note(stringResource(R.string.sound_list_own_none))
        } else {
            Column(modifier = Modifier.selectableGroup()) {
                for (row in state.own) {
                    // A row keeps what belongs to it when the one above it is removed.
                    key(row.uri) {
                        SoundRow(
                            name = ownSoundName(row.name),
                            inUse = row.inUse,
                            idle = idle,
                            onChoose = { actions.onChooseOwn(row.uri) },
                            onPlay = { actions.onPlayOwn(row.uri) },
                            onRemove = { actions.onRemove(row.uri) },
                        )
                    }
                }
            }
        }
        ActionButtonRow(
            actions =
                listOf(
                    ActionButton(
                        label = stringResource(R.string.settings_sound_add),
                        kind = ActionKind.PLAIN,
                        onClick = actions.onAdd,
                        enabled = idle,
                    ),
                ),
        )
        if (state.copying) Note(stringResource(R.string.settings_sound_copying))
        state.problem?.pickText()?.let { ToldInView(it) }
    }
}

/**
 * One sound to choose from. The row is the design's choice row, pressed as a whole, with the
 * mark of the one in use; at its end the square that plays this row's sound, and for a sound
 * of his own the small button that removes it.
 *
 * @param idle false while a picked file is being copied: the row and its buttons wait.
 * @param onRemove null for a built-in sound, which cannot be removed.
 */
@Composable
private fun SoundRow(
    name: String,
    inUse: Boolean,
    idle: Boolean,
    onChoose: () -> Unit,
    onPlay: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceRow(
            label = name,
            selected = inUse,
            onSelect = { if (idle) onChoose() },
            modifier = Modifier.weight(1f),
        )
        SquareIconButton(
            icon = MiloIcons.Play,
            description = stringResource(R.string.sound_list_play, name),
            onClick = onPlay,
            // It stands on a tile: the colour of a control, or it would not be seen.
            fill = MiloTheme.colors.control.fill,
            enabled = idle,
        )
        if (onRemove != null) {
            // TODO(debt): at the largest font size a long file name breaks into very short
            //  lines beside these two buttons (seen on an emulator at 393 dp). See
            //  docs/FINDINGS_LOG.md, 2026-10-13.
            val spoken = stringResource(R.string.sound_list_remove, name)
            RowButton(
                text = stringResource(R.string.settings_sound_remove),
                onClick = onRemove,
                // Every such button says "Remove": a screen reader is told which sound.
                modifier = Modifier.semantics { contentDescription = spoken },
                enabled = idle,
            )
        }
    }
}

/**
 * A press that did not work, said in red, and moved into view if it comes up below the edge of
 * the screen: the lists are long, and the press would otherwise look as if it had done nothing.
 */
@Composable
internal fun ToldInView(text: String) {
    val line = remember { BringIntoViewRequester() }
    LaunchedEffect(text) {
        // The line is laid out in the frame that has just begun. One frame on it has its
        // place, and the screen can tell how far to move.
        withFrameNanos { }
        line.bringIntoView()
    }
    StatusRow(
        label = text,
        status = RowStatus.PROBLEM,
        modifier = Modifier.bringIntoViewRequester(line),
    )
}

/** The sentence for a pick that did not become a sound, or null for what is said elsewhere. */
@Composable
private fun SoundListProblem.pickText(): String? = when (this) {
    SoundListProblem.COULD_NOT_COPY -> stringResource(R.string.settings_sound_refused_copy)

    SoundListProblem.TOO_LARGE ->
        stringResource(
            R.string.settings_sound_refused_large,
            MAX_OWN_SOUND_BYTES / BYTES_PER_MEGABYTE,
        )

    SoundListProblem.NOT_PLAYABLE -> stringResource(R.string.settings_sound_refused_playable)

    SoundListProblem.NO_FILE_PICKER -> stringResource(R.string.settings_sound_no_picker)

    // Said at the top of the screen: it can be about any row.
    SoundListProblem.COULD_NOT_SAVE -> null
}
