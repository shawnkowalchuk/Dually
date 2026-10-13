package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.settings.firstBuiltIn
import com.shawnkowalchuk.milo.platform.trip.OwnTripSound
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How long the state stays current after the screen stopped watching it. */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * The screen of one of the two sounds (`SoundListScreen`, since 2026-10-13): which sound it
 * plays, chosen from the built-in sounds and from Shawn's own. What the two sound tiles of the
 * Settings screen did until then is here, and was that screen's ViewModel's before.
 *
 * It keeps no copy of the settings: what the screen shows is the settings store's own flow, and
 * every press writes to the store. Nothing tells the trip engine: the trip service reads the
 * settings each time it plays a sound.
 *
 * @param which the sound this screen is about. Each of the two has a screen of its own.
 * @param ownSound chooses a sound: copies and checks a picked audio file, makes a built-in
 * sound or one from the list the sound in use, and takes one off the list.
 * @param playSound plays a sound the way a trip does, for the Play button of a row: MilO's copy
 * of a file of Shawn's own, or with null the built-in sound, which is also what is played in
 * the file's place if it cannot be. Called on the main thread.
 * @param stopSound cuts that sound off. Called when the screen is left.
 * @param clock wall-clock milliseconds.
 */
class SoundListViewModel(
    val which: TripSound,
    private val settings: SettingsStore,
    private val ownSound: OwnTripSound,
    private val playSound: (which: TripSound, ownSoundUri: String?, builtIn: BuiltInSound) -> Unit,
    private val stopSound: () -> Unit,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    /** What the screen shows that is not a stored setting. */
    private data class Passing(val copying: Boolean = false, val problem: SoundListProblem? = null)

    private val passing = MutableStateFlow(Passing())

    /** One change at a time, in the order the presses were made. */
    private val oneChangeAtATime = Mutex()

    val state: StateFlow<SoundListUiState> =
        combine(storedSettings(), passing) { stored, now ->
            if (stored == null) {
                SoundListUiState.Unreadable
            } else {
                soundListState(stored, which, now.copying, now.problem)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(KEEP_WATCHING_MS),
            SoundListUiState.Reading,
        )

    /** A press on a built-in sound's row: it is the sound in use at once. */
    fun onChooseBuiltIn(sound: BuiltInSound) = change { ownSound.useBuiltIn(which, sound) }

    /** A press on the row of one of his own sounds, named by MilO's copy of it. */
    fun onChooseOwn(ownSoundUri: String) = change { ownSound.useOwn(which, ownSoundUri) }

    /**
     * Takes one of his own sounds off the list, for both sounds. Whatever a Play button of this
     * screen had started is cut off first: it may be the sound that is going.
     */
    fun onRemove(ownSoundUri: String) {
        stopSound()
        change { ownSound.remove(ownSoundUri) }
    }

    /** Plays a row's sound, whether or not it is the one in use, and whatever the switch says. */
    fun onPlayBuiltIn(sound: BuiltInSound) = playSound(which, null, sound)

    /**
     * Plays one of his own sounds as a trip would play it: if its copy cannot be played, the
     * first built-in sound of this list is heard in its place, and the Log says why.
     */
    fun onPlayOwn(ownSoundUri: String) = playSound(which, ownSoundUri, which.firstBuiltIn)

    /**
     * Shawn picked a file. It is copied and checked first; if that fails the screen says why,
     * and the sound that was in use stays.
     */
    fun onOwnSoundPicked(uri: String) {
        viewModelScope.launch {
            passing.value = Passing(copying = true)
            val refusal = ownSound.choose(which, uri)
            passing.value = Passing(problem = refusal?.asProblem())
        }
    }

    /** The phone could not show a file picker at all. */
    fun onNoFilePicker() {
        passing.update { it.copy(problem = SoundListProblem.NO_FILE_PICKER) }
    }

    /** The screen is no longer in front: what a Play button started is cut off. */
    fun onLeft() = stopSound()

    /** And so it is when the screen is closed. */
    override fun onCleared() = stopSound()

    /**
     * Runs one press. A settings file that cannot be read or written is said on the screen and
     * written to the event log; left alone, the exception would end the process, and the trip
     * service runs in it.
     */
    private fun change(write: suspend () -> Unit) {
        viewModelScope.launch {
            val failure =
                try {
                    oneChangeAtATime.withLock { write() }
                    null
                } catch (notStored: IOException) {
                    notStored
                }
            passing.update {
                it.copy(problem = SoundListProblem.COULD_NOT_SAVE.takeIf { failure != null })
            }
            failure?.let {
                val what = "The Settings screen could not store a change"
                eventLog.add(clock(), EventCategory.ERROR, what, it.stackTraceToString())
            }
        }
    }

    /** The stored settings, or null once the file has turned out to be unreadable. */
    private fun storedSettings(): Flow<MiloSettings?> = settings.settings
        .map<MiloSettings, MiloSettings?> { it }
        .catch { unreadable ->
            if (unreadable !is IOException) throw unreadable
            val what = "The Settings screen could not read the settings"
            eventLog.add(clock(), EventCategory.ERROR, what, unreadable.stackTraceToString())
            emit(null)
        }
}
