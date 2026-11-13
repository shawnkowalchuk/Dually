package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

// The sounds built into MilO, and which of them each of the two sounds ([TripSound]) plays while
// it plays none of Shawn's own. Until 2026-10-13 there were two, one for each: the connect
// sound's chirp and the trip-start sound's chime. Since then there are twelve, and, as with the
// sounds he adds himself, they are one list that both choose from.
//
// Which one is chosen is one more value for each sound, beside the three it had (`SettingsStore`
// has those keys). A file that holds no such value is every file written before 2026-10-13, and
// reads as it always did: the chirp at the connect and the chime at the drive-off.

/**
 * A sound built into MilO. Each is a file in `res/raw/` (`platform/trip/BundledSounds.kt` says
 * which), made by a script in `tools/` and by no recording.
 *
 * The order here is the order the lists show them in ([builtInSounds]).
 *
 * @param storedName what a choice of it is stored as in the settings file. It is written to
 * that file, so it must never change, even if the constant is renamed.
 * @param suits the moment it was made for: the short ones for the connect, the livelier ones
 * for the drive-off (Shawn's sorting of 2026-10-13). Either moment may play any of them; this
 * only decides which stand first in a moment's list.
 */
enum class BuiltInSound(val storedName: String, val suits: TripSound) {
    /** The droid-style chirp: the connect sound since MilO's first day. */
    CHIRP("chirp", TripSound.CONNECT),
    SEATBELT_CHIME("seatbelt_chime", TripSound.CONNECT),
    KEY_FOB("key_fob", TripSound.CONNECT),
    TURN_SIGNAL("turn_signal", TripSound.CONNECT),
    COUNTER_BELL("counter_bell", TripSound.CONNECT),
    COIN("coin", TripSound.CONNECT),
    SONAR_PING("sonar_ping", TripSound.CONNECT),

    /** The four rising notes: the trip-start sound since there is one (2026-10-08). */
    CHIME("chime", TripSound.DRIVING_OFF),
    HORN_TAP("horn_tap", TripSound.DRIVING_OFF),
    ENGINE_REV("engine_rev", TripSound.DRIVING_OFF),
    BURNOUT("burnout", TripSound.DRIVING_OFF),
    RACE_START("race_start", TripSound.DRIVING_OFF),
}

/**
 * Every built-in sound, in the order the list of [this] moment shows them: first the ones that
 * suit it, then the others, each group in the order of [BuiltInSound].
 */
fun TripSound.builtInSounds(): List<BuiltInSound> {
    val (suited, others) = BuiltInSound.entries.partition { it.suits == this }
    return suited + others
}

/**
 * The first sound of [this] moment's list: the chirp for the connect, the chime for the
 * drive-off. It is what the moment plays
 * - until another sound is chosen, so a MilO that is updated plays what it always played;
 * - when the settings name a built-in sound this build does not have (a later version's, after
 *   a restore or an import);
 * - and in the place of a sound of Shawn's own that cannot be played, or that he removed.
 */
val TripSound.firstBuiltIn: BuiltInSound
    get() = builtInSounds().first()

private val CONNECT_BUILT_IN_SOUND = stringPreferencesKey("connect_built_in_sound")
private val DRIVING_OFF_BUILT_IN_SOUND = stringPreferencesKey("driving_off_built_in_sound")

/** Where the built-in sound chosen for [this] sound is kept. */
internal val TripSound.builtInKey: Preferences.Key<String>
    get() = when (this) {
        TripSound.CONNECT -> CONNECT_BUILT_IN_SOUND
        TripSound.DRIVING_OFF -> DRIVING_OFF_BUILT_IN_SOUND
    }

/**
 * The built-in sound chosen for [which], or null: none was ever chosen, or the file names one
 * this build does not know. The stored name is left as it is either way, so a later version
 * that has that sound plays it again.
 */
internal fun Preferences.readBuiltInSound(which: TripSound): BuiltInSound? {
    val stored = this[which.builtInKey] ?: return null
    return BuiltInSound.entries.firstOrNull { it.storedName == stored }
}

/**
 * Has [which] play the built-in [sound], in one write. A sound of his own that it played stays
 * on the list, to be chosen again.
 */
suspend fun SettingsStore.useBuiltInSound(which: TripSound, sound: BuiltInSound) {
    dataStore.edit { stored ->
        stored.remove(which.uriKey)
        stored.remove(which.nameKey)
        stored[which.builtInKey] = sound.storedName
    }
}
