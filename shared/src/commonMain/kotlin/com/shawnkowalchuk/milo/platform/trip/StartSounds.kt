package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.party.PopAtTripStart
import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.settings.firstBuiltIn
import com.shawnkowalchuk.milo.data.settings.partyPopHeard
import com.shawnkowalchuk.milo.data.settings.sound

// What is played at one of the moments for a sound: on every day but one the two sounds as the
// settings have them; on the day of the one-year party the trip's first sound gives way to the
// party's pop (APP_ENCYCLOPEDIA, "The one-year party"). Moved here from the app on 2026-11-16, so
// that the iPhone plays by the same rule; the playing is each phone's own.

/**
 * How long the trip service waits for the animation to take the pop it was left
 * (`PopHandover`), before it plays the pop itself. Home has by then long drawn the mascot if it
 * is going to: on an emulator that took a fifth of a second.
 */
const val POP_HANDOVER_MS = 2_000L

/** One sound the trip service plays. */
sealed interface StartSound {
    /**
     * One of the two sounds of every day.
     *
     * @param ownUri MilO's copy of the file Shawn chose for it, or null for a bundled sound.
     * @param builtIn the bundled sound it plays, or falls back on if the file at [ownUri]
     * cannot be played (`SoundChoice.builtIn`). Until one is chosen that is the first of the
     * sound's list: the chirp at the connect and the chime at the drive-off, as before there
     * was a choice (2026-10-13).
     */
    data class Usual(
        val which: TripSound,
        val ownUri: String?,
        val builtIn: BuiltInSound = which.firstBuiltIn,
    ) : StartSound

    /** The pop of MilO's one-year party. */
    data object PartyPop : StartSound
}

/**
 * What is played for [sounds], in their order.
 *
 * - **On every day but the party's** ([PopAtTripStart.NOT_TODAY]): each of them that is switched
 *   on, with the file Shawn chose for it or the bundled sound chosen for it. Nothing about the
 *   party has a say.
 * - **At a trip's start on the day of the party:** the first of them, the trip's first sound,
 *   is not played, switched on or not. The pop takes its place if the service is to play it
 *   ([PopAtTripStart.AT_ONCE]) and it is heard ([partyPopHeard]); left to the animation, nothing
 *   does. Whatever follows the first is played as on every day: at a connect that is the
 *   trip-start sound, when the truck is already driving.
 *
 * @param pop how the party's pop is played, for sounds that are a trip's first. For any other
 * moment it is [PopAtTripStart.NOT_TODAY], whatever the day.
 */
fun soundsToPlay(
    sounds: List<TripSound>,
    pop: PopAtTripStart,
    settings: MiloSettings,
): List<StartSound> {
    fun asSet(which: List<TripSound>): List<StartSound> = which
        .map { it to settings.sound(it) }
        .filter { (_, choice) -> choice.enabled }
        .map { (sound, choice) -> StartSound.Usual(sound, choice.ownUri, choice.builtIn) }
    if (pop == PopAtTripStart.NOT_TODAY || sounds.isEmpty()) return asSet(sounds)
    val popNow = pop == PopAtTripStart.AT_ONCE && settings.partyPopHeard
    return listOfNotNull(StartSound.PartyPop.takeIf { popNow }) + asSet(sounds.drop(1))
}
