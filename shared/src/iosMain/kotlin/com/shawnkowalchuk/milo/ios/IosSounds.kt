package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.party.PopAtTripStart
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.settings.addOwnSound
import com.shawnkowalchuk.milo.data.settings.builtInSounds
import com.shawnkowalchuk.milo.data.settings.chooseOwnSound
import com.shawnkowalchuk.milo.data.settings.removeOwnSound
import com.shawnkowalchuk.milo.data.settings.setSoundEnabled
import com.shawnkowalchuk.milo.data.settings.sound
import com.shawnkowalchuk.milo.data.settings.useBuiltInSound
import com.shawnkowalchuk.milo.platform.trip.StartSound
import com.shawnkowalchuk.milo.platform.trip.soundsToPlay

/**
 * The connect and trip-start sounds on the iPhone (2026-11-16), by Android's rules and in its
 * settings: which sound each moment plays (`soundsToPlay`, shared), the Sounds tile and its
 * page, and the Log's lines. Swift plays them (`TripSoundPlayer`), through the silent switch and
 * a Focus, as Shawn chose for the iPhone ("Play through silent").
 *
 * @param partyAtTripStart whether today the trip's first sound gives way to the one-year party's
 * pop, asked only for a trip's first sound.
 * @param clock wall-clock milliseconds, for the event log.
 */
class IosSounds internal constructor(
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val partyAtTripStart: suspend () -> PopAtTripStart = { PopAtTripStart.NOT_TODAY },
) {
    /**
     * What to play for [due] (from `IosTripRecorder.takeSounds`), in its order: each a file built
     * into the app, a sound of the user's own with that file to fall back on, or the party's pop.
     */
    suspend fun toPlay(due: TripSounds): List<SoundToPlay> {
        val now = current()
        val sounds = due.sounds.mapNotNull { name ->
            TripSound.entries.firstOrNull { it.name == name }
        }
        val pop = if (due.tripStarts) partyAtTripStart() else PopAtTripStart.NOT_TODAY
        return soundsToPlay(sounds, pop, now).map { sound ->
            when (sound) {
                is StartSound.Usual ->
                    SoundToPlay(
                        label = sound.which.label(),
                        file = fileOf(sound.builtIn),
                        builtInName = nameOf(sound.builtIn),
                        own = sound.ownUri,
                    )

                StartSound.PartyPop -> SoundToPlay("Party pop", PARTY_POP_FILE, "party pop", null)
            }
        }
    }

    /** Settings' Sounds tile: each moment's switch and the sound in use. */
    suspend fun tile(): List<SoundMomentText> {
        val now = current()
        return TripSound.entries.map { which ->
            val choice = now.sound(which)
            SoundMomentText(
                which = which.name,
                title = if (which == TripSound.CONNECT) "Connect" else "Trip start",
                enabled = choice.enabled,
                inUse = choice.ownName?.takeIf { choice.ownUri != null }
                    ?: choice.ownUri?.let { "Your own sound" }
                    ?: nameOf(choice.builtIn),
            )
        }
    }

    /** A moment's page: its words, the built-in sounds in its order, and the user's own. */
    suspend fun page(which: String): SoundPageText {
        val moment = momentOf(which)
        val now = current()
        val choice = now.sound(moment)
        return SoundPageText(
            which = moment.name,
            title = if (moment == TripSound.CONNECT) "Connect sound" else "Trip-start sound",
            whenItPlays =
                if (moment == TripSound.CONNECT) {
                    "Plays when Dually learns the truck is connected and a trip begins, or when " +
                        "Start is pressed."
                } else {
                    "Plays once a trip is under way: the first time Dually sees the truck moving " +
                        "at driving speed. Also when a parked truck drives off."
                },
            enabled = choice.enabled,
            builtIn =
                moment.builtInSounds().map { sound ->
                    SoundRowText(
                        key = sound.storedName,
                        name = nameOf(sound),
                        file = fileOf(sound),
                        chosen = choice.ownUri == null && choice.builtIn == sound,
                    )
                },
            own =
                now.ownSounds.map { own ->
                    SoundRowText(
                        key = own.uri,
                        name = own.name ?: "Your own sound",
                        file = own.uri,
                        chosen = choice.ownUri == own.uri,
                    )
                },
        )
    }

    suspend fun setEnabled(which: String, enabled: Boolean) =
        settings.setSoundEnabled(momentOf(which), enabled)

    suspend fun useBuiltIn(which: String, storedName: String) {
        val sound = BuiltInSound.entries.firstOrNull { it.storedName == storedName } ?: return
        settings.useBuiltInSound(momentOf(which), sound)
    }

    /** A sound the user added: [path] is Dually's own copy of the file, in its storage. */
    suspend fun addOwn(which: String, path: String, name: String?) =
        settings.addOwnSound(momentOf(which), path, name?.takeIf { it.isNotBlank() })

    suspend fun chooseOwn(which: String, path: String): Boolean =
        settings.chooseOwnSound(momentOf(which), path)

    suspend fun removeOwn(path: String) = settings.removeOwnSound(path)

    /** A line about a sound for the Log, in Android's words ("Connect sound: playing …"). */
    suspend fun log(line: String, problem: Boolean) =
        eventLog.add(clock(), if (problem) EventCategory.ERROR else EventCategory.TRIP, line)

    private suspend fun current(): MiloSettings = try {
        settings.current()
    } catch (unreadable: Exception) {
        eventLog.add(clock(), EventCategory.ERROR, "Sound settings unreadable: $unreadable")
        MiloSettings()
    }

    private fun momentOf(which: String) =
        TripSound.entries.firstOrNull { it.name == which } ?: TripSound.CONNECT

    private fun TripSound.label() = if (this ==
        TripSound.CONNECT
    ) {
        "Connect sound"
    } else {
        "Trip-start sound"
    }

    private companion object {
        const val PARTY_POP_FILE = "party_pop"

        /** The file of a built-in sound in the app, Android's raw resource's name. */
        fun fileOf(sound: BuiltInSound): String = when (sound) {
            BuiltInSound.CHIRP -> "trip_start_chirp"
            BuiltInSound.CHIME -> "trip_go_chime"
            else -> "sound_" + sound.storedName
        }

        /** Its name in the lists, Android's words. */
        fun nameOf(sound: BuiltInSound): String = when (sound) {
            BuiltInSound.CHIRP -> "Chirp"
            BuiltInSound.SEATBELT_CHIME -> "Seatbelt chime"
            BuiltInSound.KEY_FOB -> "Key fob"
            BuiltInSound.TURN_SIGNAL -> "Turn signal"
            BuiltInSound.COUNTER_BELL -> "Counter bell"
            BuiltInSound.COIN -> "Coin"
            BuiltInSound.SONAR_PING -> "Sonar ping"
            BuiltInSound.CHIME -> "Chime"
            BuiltInSound.HORN_TAP -> "Horn tap"
            BuiltInSound.ENGINE_REV -> "Engine rev"
            BuiltInSound.BURNOUT -> "Burnout"
            BuiltInSound.RACE_START -> "Race start"
        }
    }
}

/**
 * One sound to play: [label] for the Log ("Connect sound"), the built-in [file] (a `.wav` in the
 * app) and its [builtInName], and [own], the path of the user's own sound, played instead when
 * there is one, with [file] as what plays if it cannot.
 */
data class SoundToPlay(
    val label: String,
    val file: String,
    val builtInName: String,
    val own: String?,
)

/** A moment on the Sounds tile: "CONNECT", "Connect", its switch and the sound in use. */
data class SoundMomentText(
    val which: String,
    val title: String,
    val enabled: Boolean,
    val inUse: String,
)

/** A moment's page of sounds. */
data class SoundPageText(
    val which: String,
    val title: String,
    val whenItPlays: String,
    val enabled: Boolean,
    val builtIn: List<SoundRowText>,
    val own: List<SoundRowText>,
)

/** One sound in a list: its key (a built-in's stored name, or an own sound's path), its name. */
data class SoundRowText(val key: String, val name: String, val file: String, val chosen: Boolean)
