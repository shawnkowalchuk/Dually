package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.settings.builtInSounds
import com.shawnkowalchuk.milo.data.settings.sound
import com.shawnkowalchuk.milo.platform.trip.OwnSoundRefusal

// What the screen of one sound shows (`SoundListScreen`), and the function that decides it from
// the stored settings. Pure, so it is tested without a phone.

/** Something the screen has to tell Shawn about a press that did not do what it said. */
enum class SoundListProblem {
    /** The picked file could not be read or copied. */
    COULD_NOT_COPY,

    /** The picked file is larger than MilO copies. */
    TOO_LARGE,

    /** The picked file was copied, but Android cannot play it. */
    NOT_PLAYABLE,

    /** The phone has no app that can show a file picker. */
    NO_FILE_PICKER,

    /** The settings file could not be written or read: the change was not made. */
    COULD_NOT_SAVE,
}

/**
 * One built-in sound in the list.
 *
 * @param inUse whether it is the sound this screen's sound plays now.
 */
data class BuiltInRow(val sound: BuiltInSound, val inUse: Boolean)

/**
 * One sound of Shawn's own in the list.
 *
 * @param uri MilO's copy of the file, by which it is chosen, played and removed.
 * @param name what the file he picked was called, or null if the phone gave no name.
 * @param inUse whether it is the sound this screen's sound plays now.
 */
data class OwnRow(val uri: String, val name: String?, val inUse: Boolean)

/** What the screen of one sound shows. */
sealed interface SoundListUiState {
    /** The settings have not been read yet. */
    data object Reading : SoundListUiState

    /** The settings file cannot be read, so there is nothing true to show or to choose. */
    data object Unreadable : SoundListUiState

    /**
     * The sounds to choose from. Exactly one row of the two lists is in use.
     *
     * @param which the sound this screen is about.
     * @param builtIn the twelve built-in sounds: first the ones that suit [which], then the
     * others (`builtInSounds`).
     * @param own every sound of his own, in the order they were added. The list is the same on
     * both sounds' screens.
     * @param copying true while a picked file is being copied and checked. Every button and
     * every row waits: the answer changes the list, and which row is in use.
     * @param problem the last press that did not work, until the next one.
     */
    data class Ready(
        val which: TripSound,
        val builtIn: List<BuiltInRow>,
        val own: List<OwnRow>,
        val copying: Boolean,
        val problem: SoundListProblem?,
    ) : SoundListUiState
}

/** The screen of [which] for the settings as they are stored. */
fun soundListState(
    settings: MiloSettings,
    which: TripSound,
    copying: Boolean = false,
    problem: SoundListProblem? = null,
): SoundListUiState.Ready {
    val inUse = settings.sound(which)
    return SoundListUiState.Ready(
        which = which,
        builtIn =
            which.builtInSounds().map { sound ->
                BuiltInRow(sound, inUse = inUse.ownUri == null && inUse.builtIn == sound)
            },
        own = settings.ownSounds.map { OwnRow(it.uri, it.name, inUse = it.uri == inUse.ownUri) },
        copying = copying,
        problem = problem,
    )
}

/** What the screen says when a picked file was refused. */
fun OwnSoundRefusal.asProblem(): SoundListProblem = when (this) {
    OwnSoundRefusal.COULD_NOT_COPY -> SoundListProblem.COULD_NOT_COPY
    OwnSoundRefusal.TOO_LARGE -> SoundListProblem.TOO_LARGE
    OwnSoundRefusal.NOT_PLAYABLE -> SoundListProblem.NOT_PLAYABLE
}
