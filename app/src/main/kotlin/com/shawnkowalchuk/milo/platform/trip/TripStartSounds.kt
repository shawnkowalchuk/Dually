package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.party.PopAtTripStart
import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.settings.firstBuiltIn
import com.shawnkowalchuk.milo.data.settings.partyPopHeard
import com.shawnkowalchuk.milo.data.settings.sound
import com.shawnkowalchuk.milo.platform.party.OneYearParty
import java.io.IOException
import java.util.concurrent.Executor
import kotlinx.coroutines.delay

// What the trip service plays at one of its moments for a sound. On every day but one that is
// the two sounds as the settings have them, exactly as it was before MilO had a party. On the
// day of the one-year party the trip's first sound gives way to the party's pop
// (APP_ENCYCLOPEDIA, "The one-year party"). Which sounds is a plain function with unit tests;
// the playing is the service's own player.

/**
 * How long the trip service waits for the animation to take the pop it was left
 * (`PopHandover`), before it plays the pop itself. Home has by then long drawn the mascot if it
 * is going to: on an emulator that took a fifth of a second.
 */
internal const val POP_HANDOVER_MS = 2_000L

/** One sound the trip service plays. */
internal sealed interface StartSound {
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
internal fun soundsToPlay(
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

/**
 * Works out what one moment of the trip service plays, and has [play] play it: [sounds], in
 * their order, as the settings have them, and on the day of MilO's party the pop in place of a
 * trip's first sound. The settings are read once for all of them.
 *
 * **A pop that was left to the animation is waited for** ([POP_HANDOVER_MS]): if no animation
 * has taken it by then, it is played here after all, so that a trip's start is never silent
 * because Home was left in that instant.
 *
 * @param tripStarts whether [sounds] holds the trip's first sound (`TripRecorder.record`). Only
 * then is the party asked what day it is.
 * @param onUnreadable a line for the event log if the settings cannot be read. The controller
 * logs the unreadable file itself; both sounds are on by default, so the bundled ones play.
 * @param play plays some sounds one after the other. Not called with none.
 */
internal suspend fun soundsAtMoment(
    sounds: List<TripSound>,
    tripStarts: Boolean,
    party: OneYearParty,
    settings: SettingsStore,
    onUnreadable: (String) -> Unit,
    play: (List<StartSound>) -> Unit,
) {
    val now =
        try {
            settings.current()
        } catch (unreadable: IOException) {
            onUnreadable("Sound settings unreadable: $unreadable")
            MiloSettings()
        }
    val pop = if (tripStarts) party.atTripStart() else PopAtTripStart.NOT_TODAY
    val toPlay = soundsToPlay(sounds, pop, now)
    if (toPlay.isNotEmpty()) play(toPlay)
    if (pop != PopAtTripStart.WITH_THE_ANIMATION) return
    delay(POP_HANDOVER_MS)
    // Taken whether it is heard or not, so that nothing is left for a later showing to find.
    if (party.takePop() && now.partyPopHeard) play(listOf(StartSound.PartyPop))
}

/**
 * [soundsAtMoment], played by this player.
 *
 * @param mainThread where the sounds are played ([TripSoundPlayer.play] must run there).
 */
internal suspend fun TripSoundPlayer.playAtMoment(
    sounds: List<TripSound>,
    tripStarts: Boolean,
    party: OneYearParty,
    settings: SettingsStore,
    mainThread: Executor,
    onUnreadable: (String) -> Unit,
) = soundsAtMoment(sounds, tripStarts, party, settings, onUnreadable) { toPlay ->
    mainThread.execute {
        for (sound in toPlay) {
            when (sound) {
                is StartSound.Usual -> play(sound.which, sound.ownUri, sound.builtIn)
                StartSound.PartyPop -> playPartyPop()
            }
        }
    }
}
