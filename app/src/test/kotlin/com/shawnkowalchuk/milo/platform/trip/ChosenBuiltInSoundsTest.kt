package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.party.PopAtTripStart
import com.shawnkowalchuk.milo.core.party.TripStart
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.settings.addOwnSound
import com.shawnkowalchuk.milo.data.settings.setSoundEnabled
import com.shawnkowalchuk.milo.data.settings.useBuiltInSound
import com.shawnkowalchuk.milo.platform.party.OneYearParty
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

private fun at(year: Int, day: Int): Long =
    LocalDateTime.of(year, 10, day, 7, 42).atZone(EDMONTON).toInstant().toEpochMilli()

private const val OWN = "file:/data/trip_sound/own_trip_start_sound_1"

/**
 * What the trip service plays once a built-in sound can be chosen for each of the two sounds
 * (since 2026-10-13). `TripStartSoundsTest` holds that a MilO in which nothing was chosen plays
 * what it always played; this is the choice itself: every one of the twelve for either sound,
 * the switches, a sound of his own with what stands behind it, the order, and the party's day.
 */
class ChosenBuiltInSoundsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val today = PopAtTripStart.NOT_TODAY
    private val both = listOf(TripSound.CONNECT, TripSound.DRIVING_OFF)

    @Test
    fun `out of the box the service is handed the chirp and the chime by name`() {
        assertEquals(
            listOf(
                StartSound.Usual(TripSound.CONNECT, ownUri = null, BuiltInSound.CHIRP),
                StartSound.Usual(TripSound.DRIVING_OFF, ownUri = null, BuiltInSound.CHIME),
            ),
            soundsToPlay(both, today, MiloSettings()),
        )
    }

    @Test
    fun `each of the twelve is played for the sound it was chosen for, and only there`() {
        for (sound in BuiltInSound.entries) {
            val forConnect = MiloSettings(connectBuiltIn = sound)
            val forDrivingOff = MiloSettings(drivingOffBuiltIn = sound)

            assertEquals(
                "$sound for the connect sound",
                listOf(
                    StartSound.Usual(TripSound.CONNECT, null, sound),
                    StartSound.Usual(TripSound.DRIVING_OFF, null, BuiltInSound.CHIME),
                ),
                soundsToPlay(both, today, forConnect),
            )
            assertEquals(
                "$sound for the trip-start sound",
                listOf(
                    StartSound.Usual(TripSound.CONNECT, null, BuiltInSound.CHIRP),
                    StartSound.Usual(TripSound.DRIVING_OFF, null, sound),
                ),
                soundsToPlay(both, today, forDrivingOff),
            )
        }
    }

    @Test
    fun `the connect sound comes before the trip-start sound, whatever each plays`() {
        // A sound made for the other moment chosen for each: the order is the moments', not
        // the sounds'.
        val swapped =
            MiloSettings(
                connectBuiltIn = BuiltInSound.RACE_START,
                drivingOffBuiltIn = BuiltInSound.KEY_FOB,
            )

        assertEquals(
            listOf(
                StartSound.Usual(TripSound.CONNECT, null, BuiltInSound.RACE_START),
                StartSound.Usual(TripSound.DRIVING_OFF, null, BuiltInSound.KEY_FOB),
            ),
            soundsToPlay(both, today, swapped),
        )
    }

    @Test
    fun `a sound that is switched off is not played, whichever sound was chosen for it`() {
        for (sound in BuiltInSound.entries) {
            val settings =
                MiloSettings(
                    soundEnabled = false,
                    connectBuiltIn = sound,
                    drivingOffBuiltIn = sound,
                )

            assertEquals(
                listOf<StartSound>(StartSound.Usual(TripSound.DRIVING_OFF, null, sound)),
                soundsToPlay(both, today, settings),
            )
            assertEquals(
                emptyList<StartSound>(),
                soundsToPlay(both, today, settings.copy(drivingOffSoundEnabled = false)),
            )
        }
    }

    @Test
    fun `behind a sound of his own stands the first sound of the list, never an earlier choice`() {
        // No setter leaves a built-in choice beside a sound of his own; a file that has both
        // still plays his own, and falls back on the chirp or the chime.
        val settings =
            MiloSettings(
                customSoundUri = OWN,
                connectBuiltIn = BuiltInSound.COIN,
                drivingOffSoundUri = OWN,
                drivingOffBuiltIn = BuiltInSound.BURNOUT,
            )

        assertEquals(
            listOf(
                StartSound.Usual(TripSound.CONNECT, OWN, BuiltInSound.CHIRP),
                StartSound.Usual(TripSound.DRIVING_OFF, OWN, BuiltInSound.CHIME),
            ),
            soundsToPlay(both, today, settings),
        )
    }

    @Test
    fun `on the day of the party the pop takes the first sound's place, whatever was chosen`() {
        val settings =
            MiloSettings(
                connectBuiltIn = BuiltInSound.SEATBELT_CHIME,
                drivingOffBuiltIn = BuiltInSound.ENGINE_REV,
            )

        assertEquals(
            listOf(
                StartSound.PartyPop,
                StartSound.Usual(TripSound.DRIVING_OFF, null, BuiltInSound.ENGINE_REV),
            ),
            soundsToPlay(both, PopAtTripStart.AT_ONCE, settings),
        )
        // A trip a parked truck's moving started: its trip-start sound is its first.
        assertEquals(
            listOf<StartSound>(StartSound.PartyPop),
            soundsToPlay(listOf(TripSound.DRIVING_OFF), PopAtTripStart.AT_ONCE, settings),
        )
        // Left to the animation, the service plays neither the pop nor the sound it replaces.
        assertEquals(
            listOf<StartSound>(
                StartSound.Usual(TripSound.DRIVING_OFF, null, BuiltInSound.ENGINE_REV),
            ),
            soundsToPlay(both, PopAtTripStart.WITH_THE_ANIMATION, settings),
        )
    }

    // ---- A moment of the service, from the stored choice to what is played ------------------------

    private val log = FakeEventLogDao()
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)
    private val played = mutableListOf<List<StartSound>>()

    /** A day that is not the day of the party: MilO's first trip was on 6 October 2026. */
    private val party by lazy {
        OneYearParty(
            tripStarts = { listOf(TripStart(at(2026, 6), null, addedByHand = false)) },
            settings = store,
            eventLog = EventLogRepository(log),
            crashFileStore = CrashFileStore(folder.root),
            clock = { at(2027, 5) },
            zone = { EDMONTON },
        )
    }

    private suspend fun moment(sounds: List<TripSound>) =
        soundsAtMoment(sounds, tripStarts = true, party, store, { error(it) }, played::add)

    @Test
    fun `a choice made in Settings is what the next trip plays, with no restart`() = runTest {
        moment(listOf(TripSound.CONNECT))
        store.useBuiltInSound(TripSound.CONNECT, BuiltInSound.SONAR_PING)
        moment(listOf(TripSound.CONNECT))

        assertEquals(
            listOf(
                listOf<StartSound>(StartSound.Usual(TripSound.CONNECT, null, BuiltInSound.CHIRP)),
                listOf<StartSound>(
                    StartSound.Usual(TripSound.CONNECT, null, BuiltInSound.SONAR_PING),
                ),
            ),
            played,
        )
    }

    @Test
    fun `a sound of his own, then removed or switched off, is followed at the next moment`() =
        runTest {
            store.useBuiltInSound(TripSound.DRIVING_OFF, BuiltInSound.HORN_TAP)
            store.addOwnSound(TripSound.DRIVING_OFF, OWN, "mk64_racestart.mp3")
            moment(listOf(TripSound.DRIVING_OFF))
            store.useBuiltInSound(TripSound.DRIVING_OFF, BuiltInSound.HORN_TAP)
            moment(listOf(TripSound.DRIVING_OFF))
            store.setSoundEnabled(TripSound.DRIVING_OFF, false)
            moment(listOf(TripSound.DRIVING_OFF))

            assertEquals(
                listOf(
                    // His own, and the chime if its copy cannot be played.
                    listOf<StartSound>(
                        StartSound.Usual(TripSound.DRIVING_OFF, OWN, BuiltInSound.CHIME),
                    ),
                    listOf<StartSound>(
                        StartSound.Usual(TripSound.DRIVING_OFF, null, BuiltInSound.HORN_TAP),
                    ),
                    // Switched off: nothing is handed to the player at all.
                ),
                played,
            )
        }
}
