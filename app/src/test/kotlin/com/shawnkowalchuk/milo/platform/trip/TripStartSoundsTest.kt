package com.shawnkowalchuk.milo.platform.trip

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.core.party.PopAtTripStart
import com.shawnkowalchuk.milo.core.party.TripStart
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.settings.setSoundEnabled
import com.shawnkowalchuk.milo.data.settings.sound
import com.shawnkowalchuk.milo.platform.party.OneYearParty
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

private fun at(year: Int, day: Int = 6, hour: Int = 7): Long =
    LocalDateTime.of(year, 10, day, hour, 42).atZone(EDMONTON).toInstant().toEpochMilli()

private const val OWN_CHIRP = "file:/data/trip_sound/own_trip_start_sound_1"
private const val OWN_CHIME = "file:/data/trip_sound/own_trip_start_sound_2"

/** The sounds of every day, written out once: the two that are switched on, as chosen. */
private val CONNECT = StartSound.Usual(TripSound.CONNECT, ownUri = null)
private val DRIVING_OFF = StartSound.Usual(TripSound.DRIVING_OFF, ownUri = null)

/**
 * What the trip service plays at a moment for a sound. First that on every day but the party's
 * it is what it always was; then what the day of the party changes, and who ends up playing the
 * pop when it was left to the animation.
 */
// advanceTimeBy() and runCurrent() move the test's clock; the API is marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class TripStartSoundsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bothOn = MiloSettings()

    /** Every way the two switches and the two choices of sound can stand. */
    private val everySetting: List<MiloSettings> =
        buildList {
            for (connectOn in listOf(true, false)) {
                for (drivingOffOn in listOf(true, false)) {
                    for (chirp in listOf(null, OWN_CHIRP)) {
                        for (chime in listOf(null, OWN_CHIME)) {
                            add(
                                MiloSettings(
                                    soundEnabled = connectOn,
                                    drivingOffSoundEnabled = drivingOffOn,
                                    customSoundUri = chirp,
                                    drivingOffSoundUri = chime,
                                ),
                            )
                        }
                    }
                }
            }
        }

    /** Every list of sounds the trip controller ever sends, and none. */
    private val everyMoment: List<List<TripSound>> =
        listOf(
            emptyList(),
            listOf(TripSound.CONNECT),
            listOf(TripSound.DRIVING_OFF),
            listOf(TripSound.CONNECT, TripSound.DRIVING_OFF),
        )

    /**
     * What the trip service played before MilO had a party, word for word as `playAsSet` chose
     * it then: each sound that is switched on, with the file chosen for it.
     */
    private fun asBeforeTheParty(sounds: List<TripSound>, now: MiloSettings): List<StartSound> =
        sounds
            .map { it to now.sound(it) }
            .filter { (_, choice) -> choice.enabled }
            .map { (which, choice) -> StartSound.Usual(which, choice.ownUri) }

    // ---- Every other day: nothing has changed ----------------------------------------------------

    @Test
    fun `on every other day the sounds are exactly what they were, however the settings stand`() {
        for (settings in everySetting) {
            for (sounds in everyMoment) {
                assertEquals(
                    "$sounds with $settings",
                    asBeforeTheParty(sounds, settings),
                    soundsToPlay(sounds, PopAtTripStart.NOT_TODAY, settings),
                )
            }
        }
        // 16 ways the settings can stand, and 4 moments: none was left out.
        assertEquals(16, everySetting.toSet().size)
    }

    @Test
    fun `out of the box that is the chirp at the connect and the chime at the drive-off`() {
        val today = PopAtTripStart.NOT_TODAY
        val drivingOff = listOf(TripSound.DRIVING_OFF)

        assertEquals(listOf(CONNECT), soundsToPlay(listOf(TripSound.CONNECT), today, bothOn))
        assertEquals(listOf(DRIVING_OFF), soundsToPlay(drivingOff, today, bothOn))
        assertEquals(
            listOf(CONNECT, DRIVING_OFF),
            soundsToPlay(listOf(TripSound.CONNECT, TripSound.DRIVING_OFF), today, bothOn),
        )
    }

    @Test
    fun `a sound of his own is still played for the sound he chose it for`() {
        val settings = MiloSettings(customSoundUri = OWN_CHIRP, soundEnabled = true)

        assertEquals(
            listOf(StartSound.Usual(TripSound.CONNECT, OWN_CHIRP), DRIVING_OFF),
            soundsToPlay(TripSound.entries, PopAtTripStart.NOT_TODAY, settings),
        )
    }

    // ---- The day of the party --------------------------------------------------------------------

    @Test
    fun `on the day the pop takes the connect sound's place`() {
        val played = soundsToPlay(listOf(TripSound.CONNECT), PopAtTripStart.AT_ONCE, bothOn)

        assertEquals(listOf<StartSound>(StartSound.PartyPop), played)
    }

    @Test
    fun `on the day a trip a parked truck's moving started has the pop for its chime`() {
        // Such a trip has no connect sound: its trip-start sound is its first.
        val played = soundsToPlay(listOf(TripSound.DRIVING_OFF), PopAtTripStart.AT_ONCE, bothOn)

        assertEquals(listOf<StartSound>(StartSound.PartyPop), played)
    }

    @Test
    fun `on the day the trip-start sound that follows the first sound is played as ever`() {
        val both = listOf(TripSound.CONNECT, TripSound.DRIVING_OFF)

        assertEquals(
            listOf(StartSound.PartyPop, DRIVING_OFF),
            soundsToPlay(both, PopAtTripStart.AT_ONCE, bothOn),
        )
        // And the chime of a trip that drives off later is not a trip's first sound: the
        // service asks with NOT_TODAY then, whatever the day.
        assertEquals(
            listOf(DRIVING_OFF),
            soundsToPlay(listOf(TripSound.DRIVING_OFF), PopAtTripStart.NOT_TODAY, bothOn),
        )
    }

    @Test
    fun `left to the animation, the service plays no pop and not the sound it replaces`() {
        val both = listOf(TripSound.CONNECT, TripSound.DRIVING_OFF)
        val left = PopAtTripStart.WITH_THE_ANIMATION

        assertEquals(emptyList<StartSound>(), soundsToPlay(listOf(TripSound.CONNECT), left, bothOn))
        assertEquals(listOf(DRIVING_OFF), soundsToPlay(both, left, bothOn))
    }

    @Test
    fun `the pop has no switch of its own, it is heard while either sound is on`() {
        val connectOff = MiloSettings(soundEnabled = false)
        val chimeOff = MiloSettings(drivingOffSoundEnabled = false)
        val first = listOf(TripSound.CONNECT)
        val pop = listOf<StartSound>(StartSound.PartyPop)

        // The connect sound is switched off, and the pop still marks the day.
        assertEquals(pop, soundsToPlay(first, PopAtTripStart.AT_ONCE, connectOff))
        assertEquals(pop, soundsToPlay(first, PopAtTripStart.AT_ONCE, chimeOff))
        // A chime that is switched off stays off behind the pop.
        assertEquals(
            listOf(StartSound.PartyPop),
            soundsToPlay(TripSound.entries, PopAtTripStart.AT_ONCE, chimeOff),
        )
    }

    @Test
    fun `with both sounds switched off the day is silent`() {
        val silent = MiloSettings(soundEnabled = false, drivingOffSoundEnabled = false)

        for (pop in PopAtTripStart.entries) {
            for (sounds in everyMoment) {
                val played = soundsToPlay(sounds, pop, silent)
                assertEquals("$pop, $sounds", emptyList<StartSound>(), played)
            }
        }
    }

    @Test
    fun `the pop is played in the built-in sound's place and in his own sound's place alike`() {
        val own = MiloSettings(customSoundUri = OWN_CHIRP, drivingOffSoundUri = OWN_CHIME)

        assertEquals(
            listOf(StartSound.PartyPop, StartSound.Usual(TripSound.DRIVING_OFF, OWN_CHIME)),
            soundsToPlay(TripSound.entries, PopAtTripStart.AT_ONCE, own),
        )
    }

    // ---- A moment of the service, from asking the party to the last sound ------------------------

    private val log = FakeEventLogDao()
    private val file = FakeSettingsFile()
    private var nowMs = at(2027)
    private val played = mutableListOf<List<StartSound>>()
    private val unreadable = mutableListOf<String>()

    /** MilO's first trip was on 6 October 2026. */
    private val firstTrip = TripStart(at(2026), recordedStartedAtMs = null, addedByHand = false)

    private fun partyOn(file: DataStore<Preferences> = this.file) = OneYearParty(
        tripStarts = { listOf(firstTrip) },
        settings = SettingsStore(file),
        eventLog = EventLogRepository(log),
        crashFileStore = CrashFileStore(folder.root),
        clock = { nowMs },
        zone = { EDMONTON },
    )

    private suspend fun moment(
        party: OneYearParty,
        sounds: List<TripSound>,
        tripStarts: Boolean,
        file: DataStore<Preferences> = this.file,
    ) = soundsAtMoment(sounds, tripStarts, party, SettingsStore(file), unreadable::add, played::add)

    @Test
    fun `on any other day a trip's start plays its sounds at once and nothing follows`() = runTest {
        nowMs = at(2027, day = 5)
        val party = partyOn()
        party.homeWatching = true

        moment(party, listOf(TripSound.CONNECT), tripStarts = true)

        assertEquals(listOf(listOf<StartSound>(CONNECT)), played)
        assertEquals(0L, currentTime)
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `sounds that are not a trip's first never ask the party, even on the day`() = runTest {
        var asked = 0
        val party =
            OneYearParty(
                tripStarts = {
                    asked++
                    emptyList()
                },
                settings = SettingsStore(file),
                eventLog = EventLogRepository(log),
                crashFileStore = CrashFileStore(folder.root),
                clock = { nowMs },
                zone = { EDMONTON },
            )

        moment(party, listOf(TripSound.DRIVING_OFF), tripStarts = false)

        assertEquals(0, asked)
        assertEquals(listOf(listOf<StartSound>(DRIVING_OFF)), played)
    }

    @Test
    fun `on the day, with MilO in the background, the pop is played at once`() = runTest {
        val party = partyOn()

        moment(party, listOf(TripSound.CONNECT), tripStarts = true)

        assertEquals(listOf(listOf<StartSound>(StartSound.PartyPop)), played)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `on the day, with Home in front, the animation takes the pop and the service plays none`() =
        runTest {
            val party = partyOn()
            party.homeWatching = true

            val service = launch { moment(party, listOf(TripSound.CONNECT), tripStarts = true) }
            runCurrent()
            assertTrue(played.isEmpty())
            // A fifth of a second later the mascot is drawn, and his party takes the pop.
            advanceTimeBy(200)
            assertTrue(party.takePop())
            service.join()

            assertTrue(played.isEmpty())
            assertEquals(POP_HANDOVER_MS, currentTime)
        }

    @Test
    fun `a pop the animation never took is played by the service two seconds on`() = runTest {
        val party = partyOn()
        party.homeWatching = true

        val service = launch { moment(party, TripSound.entries, tripStarts = true) }
        runCurrent()
        // The chime of a truck already driving is not held back by any of this.
        assertEquals(listOf(listOf<StartSound>(DRIVING_OFF)), played)
        advanceTimeBy(POP_HANDOVER_MS - 1)
        runCurrent()
        assertEquals(1, played.size)
        service.join()

        assertEquals(listOf(listOf(DRIVING_OFF), listOf(StartSound.PartyPop)), played)
        // And it is taken: a party that begins later finds nothing left over.
        assertFalse(party.takePop())
    }

    @Test
    fun `with both sounds off a pop that was left is taken away without a sound`() = runTest {
        val store = SettingsStore(file)
        store.setSoundEnabled(TripSound.CONNECT, false)
        store.setSoundEnabled(TripSound.DRIVING_OFF, false)
        val party = partyOn()
        party.homeWatching = true

        moment(party, listOf(TripSound.CONNECT), tripStarts = true)

        assertTrue(played.isEmpty())
        assertFalse(party.takePop())
    }

    @Test
    fun `settings that cannot be read play the built-in sounds, as they always did`() = runTest {
        nowMs = at(2027, day = 5)
        val party = partyOn(UnreadableSettingsFile)

        moment(party, TripSound.entries, tripStarts = true, file = UnreadableSettingsFile)

        assertEquals(listOf(listOf(CONNECT, DRIVING_OFF)), played)
        assertEquals(1, unreadable.size)
        assertTrue(unreadable.single().startsWith("Sound settings unreadable"))
    }
}
