package com.shawnkowalchuk.milo.feature.party

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.core.party.TripStart
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setPartyOwedSinceMs
import com.shawnkowalchuk.milo.platform.party.OneYearParty
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

private fun at(year: Int, hour: Int, minute: Int = 0, day: Int = 6): Long =
    LocalDateTime.of(year, 10, day, hour, minute).atZone(EDMONTON).toInstant().toEpochMilli()

/**
 * The party over Home through its ViewModel, on a settings file held in memory and the real
 * [OneYearParty]: once per trip started that day, the next time Home is in front; the pop on
 * the popper's frame, and who plays it when the party is cut short; the party on demand, which
 * stores nothing.
 */
// See GreetingViewModelTest for why the main dispatcher is set and runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class PartyViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val log = FakeEventLogDao()
    private val file = FakeSettingsFile()
    private val settings = SettingsStore(file)

    /** MilO's first recorded trip, a year before the day these tests are set on. */
    private val firstTrip =
        TripStart(at(2026, 7, 42), recordedStartedAtMs = null, addedByHand = false)
    private var nowMs = at(2027, 7, 42)
    private var pops = 0
    private var heard = true

    private fun partyOn(file: DataStore<Preferences> = this.file) = OneYearParty(
        tripStarts = { listOf(firstTrip) },
        settings = SettingsStore(file),
        eventLog = EventLogRepository(log),
        crashFileStore = CrashFileStore(folder.root),
        clock = { nowMs },
        zone = { EDMONTON },
    )

    /** MilO's screens as a fresh start builds them, the settings read. Home is not in front yet. */
    private fun TestScope.screens(party: OneYearParty = partyOn()): PartyViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel =
            PartyViewModel(
                party = party,
                popHeard = { heard },
                playPop = { pops++ },
                eventLog = EventLogRepository(log),
                clock = { nowMs },
                zone = { EDMONTON },
            )
        runCurrent()
        return viewModel
    }

    private fun PartyViewModel.homeInFront(inFront: Boolean = true) {
        onHomeReady(inFront)
        onInFront(inFront)
    }

    /** The mascot's party as `MascotParty` plays it to its end. */
    private fun TestScope.playThrough(viewModel: PartyViewModel) {
        viewModel.onShown()
        runCurrent()
        viewModel.onPop()
        runCurrent()
        viewModel.onDone(popped = true)
        runCurrent()
    }

    private val errors: List<String>
        get() = log.entries.filter { it.category == EventCategory.ERROR }.map { it.message }

    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    // ---- The day's party -------------------------------------------------------------------------

    @Test
    fun `on an ordinary day nothing plays, and the greeting may go ahead`() = runTest {
        val viewModel = screens()
        viewModel.homeInFront()
        runCurrent()

        assertNull(viewModel.showing.value)
        assertEquals(false, viewModel.hasTheScreen.value)
    }

    @Test
    fun `until the settings are read the greeting is told nothing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel =
            PartyViewModel(partyOn(), { heard }, { pops++ }, EventLogRepository(log), { nowMs }) {
                EDMONTON
            }

        assertNull(viewModel.hasTheScreen.value)
        runCurrent()
        assertEquals(false, viewModel.hasTheScreen.value)
    }

    @Test
    fun `a trip started with Home in front has its party at once, the pop on the popper's frame`() =
        runTest {
            val party = partyOn()
            val viewModel = screens(party)
            viewModel.homeInFront()

            // The trip service, at the trip's first sound: Home is watching, so it plays no pop.
            party.atTripStart()
            runCurrent()

            assertEquals(PartyShowing.THE_DAY, viewModel.showing.value)
            viewModel.onShown()
            runCurrent()
            assertEquals(0, pops)
            // The animation took the pop: the service's later look finds nothing to play.
            assertFalse(party.takePop())
            viewModel.onPop()
            runCurrent()
            assertEquals(1, pops)
            viewModel.onDone(popped = true)
            runCurrent()

            assertEquals(1, pops)
            assertNull(viewModel.showing.value)
            assertNull(settings.current().partyOwedSinceMs)
        }

    @Test
    fun `a trip started with MilO in the background has its party when Home is next in front`() =
        runTest {
            val party = partyOn()
            val viewModel = screens(party)

            party.atTripStart()
            runCurrent()
            assertNull(viewModel.showing.value)
            assertEquals(nowMs, settings.current().partyOwedSinceMs)

            // He opens MilO in the afternoon.
            nowMs = at(2027, 16, 30)
            viewModel.homeInFront()
            runCurrent()

            assertEquals(PartyShowing.THE_DAY, viewModel.showing.value)
            playThrough(viewModel)
            // Its own pop, on the popper's frame: the trip's start had its pop from the service.
            assertEquals(1, pops)
            assertNull(settings.current().partyOwedSinceMs)
        }

    @Test
    fun `a MilO that is opened in another process later that day still has the party`() = runTest {
        partyOn().atTripStart()

        // Hours later Android has ended that process. A new one, its own objects, the same file.
        nowMs = at(2027, 18, 0)
        val viewModel = screens(partyOn())
        assertEquals(true, viewModel.hasTheScreen.value)
        viewModel.homeInFront()
        runCurrent()

        assertEquals(PartyShowing.THE_DAY, viewModel.showing.value)
    }

    @Test
    fun `several trips since the last showing are one party`() = runTest {
        val party = partyOn()
        val viewModel = screens(party)
        party.atTripStart()
        nowMs = at(2027, 9, 15)
        party.atTripStart()
        nowMs = at(2027, 11, 40)
        party.atTripStart()
        runCurrent()

        nowMs = at(2027, 12, 0)
        viewModel.homeInFront()
        runCurrent()
        playThrough(viewModel)

        assertNull(viewModel.showing.value)
        assertNull(settings.current().partyOwedSinceMs)
        // And MilO left and opened again shows nothing more.
        viewModel.homeInFront(false)
        viewModel.homeInFront()
        runCurrent()
        assertNull(viewModel.showing.value)
    }

    @Test
    fun `each trip of the day has a party, once`() = runTest {
        val party = partyOn()
        val viewModel = screens(party)
        viewModel.homeInFront()
        party.atTripStart()
        runCurrent()
        playThrough(viewModel)
        assertNull(viewModel.showing.value)

        nowMs = at(2027, 13, 5)
        party.atTripStart()
        runCurrent()

        assertEquals(PartyShowing.THE_DAY, viewModel.showing.value)
        playThrough(viewModel)
        assertNull(viewModel.showing.value)
        assertEquals(2, pops)
    }

    @Test
    fun `what was owed when midnight came is not shown the day after`() = runTest {
        val party = partyOn()
        val viewModel = screens(party)
        nowMs = at(2027, 22, 15)
        party.atTripStart()
        runCurrent()

        nowMs = at(2027, 6, 30, day = 7)
        viewModel.homeInFront()
        runCurrent()

        assertNull(viewModel.showing.value)
        assertEquals(false, viewModel.hasTheScreen.value)
    }

    @Test
    fun `with both of MilO's sounds off the day's party plays without its pop`() = runTest {
        heard = false
        val party = partyOn()
        val viewModel = screens(party)
        viewModel.homeInFront()
        party.atTripStart()
        runCurrent()

        playThrough(viewModel)

        assertEquals(0, pops)
    }

    // ---- A party that is cut short ---------------------------------------------------------------

    @Test
    fun `a party left before its popper went off gives the trip's start its pop as it ends`() =
        runTest {
            val party = partyOn()
            val viewModel = screens(party)
            viewModel.homeInFront()
            party.atTripStart()
            runCurrent()
            viewModel.onShown()
            runCurrent()

            // A press leaves Home a second into it.
            viewModel.onHomeReady(false)
            viewModel.onDone(popped = false)
            runCurrent()

            assertEquals(1, pops)
            assertNull(viewModel.showing.value)
            // It has been shown: it does not come again when Home is back.
            viewModel.onHomeReady(true)
            runCurrent()
            assertNull(viewModel.showing.value)
            assertEquals(1, pops)
        }

    @Test
    fun `a party left after its popper went off plays no second pop`() = runTest {
        val party = partyOn()
        val viewModel = screens(party)
        viewModel.homeInFront()
        party.atTripStart()
        runCurrent()
        viewModel.onShown()
        viewModel.onPop()
        runCurrent()

        viewModel.onInFront(false)
        viewModel.onDone(popped = true)
        runCurrent()

        assertEquals(1, pops)
    }

    @Test
    fun `a party opened later and left early owes no pop, the trip's start has had its own`() =
        runTest {
            val party = partyOn()
            val viewModel = screens(party)
            // Not in front: the service played the pop at the start.
            party.atTripStart()
            runCurrent()
            viewModel.homeInFront()
            runCurrent()
            viewModel.onShown()
            runCurrent()

            viewModel.onInFront(false)
            viewModel.onDone(popped = false)
            runCurrent()

            assertEquals(0, pops)
        }

    @Test
    fun `a party that Home left before the mascot was drawn stays owed`() = runTest {
        val party = partyOn()
        val viewModel = screens(party)
        viewModel.homeInFront()
        party.atTripStart()
        runCurrent()
        assertEquals(PartyShowing.THE_DAY, viewModel.showing.value)

        // His pictures were still being read when a press left Home.
        viewModel.onHomeReady(false)
        viewModel.onDone(popped = false)
        runCurrent()

        assertNull(viewModel.showing.value)
        assertEquals(nowMs, settings.current().partyOwedSinceMs)
        // Nobody took the pop: the trip service plays it after its wait.
        assertTrue(party.takePop())
        // And the party comes when Home is in front again.
        viewModel.onHomeReady(true)
        runCurrent()
        assertEquals(PartyShowing.THE_DAY, viewModel.showing.value)
    }

    @Test
    fun `a party that ends with nothing drawn while Home watches is not begun again`() = runTest {
        val party = partyOn()
        val viewModel = screens(party)
        viewModel.homeInFront()
        party.atTripStart()
        runCurrent()

        // The drawing found the phone's animations off after all, and ended at once.
        viewModel.onDone(popped = false)
        runCurrent()

        assertNull(viewModel.showing.value)
        assertEquals(0, pops)
        // The pop was not taken: the trip service plays it.
        assertTrue(party.takePop())
    }

    @Test
    fun `pictures that cannot be read are one line in the Log, and nothing is tried again`() =
        runTest {
            val party = partyOn()
            val viewModel = screens(party)
            viewModel.homeInFront()
            party.atTripStart()
            runCurrent()

            viewModel.onUnreadable(IOException("mascot_party_pop is not an animated picture"))
            viewModel.onDone(popped = false)
            runCurrent()

            assertEquals(
                listOf(
                    "Dually's mascot did not hold his party: a picture of him could not be read",
                ),
                errors,
            )
            assertNull(viewModel.showing.value)
            assertNull(settings.current().partyOwedSinceMs)
            assertEquals(0, pops)
            // The sound is the trip service's to play, as when MilO is not in front.
            assertTrue(party.takePop())
            viewModel.homeInFront(false)
            viewModel.homeInFront()
            runCurrent()
            assertNull(viewModel.showing.value)
            assertEquals(1, errors.size)
        }

    @Test
    fun `a settings file that cannot be read owes no party and holds no greeting back`() = runTest {
        val viewModel = screens(partyOn(UnreadableSettingsFile))
        viewModel.homeInFront()
        runCurrent()

        assertNull(viewModel.showing.value)
        assertEquals(false, viewModel.hasTheScreen.value)
    }

    // ---- Who is told that Home is watching -------------------------------------------------------

    @Test
    fun `the trip service is told at once whether Home is in front`() = runTest {
        val party = partyOn()
        val viewModel = screens(party)
        assertFalse(party.homeWatching)

        viewModel.onHomeReady(true)
        assertFalse(party.homeWatching)
        viewModel.onInFront(true)
        assertTrue(party.homeWatching)
        // Another screen on top, or the phone's animations switched off.
        viewModel.onHomeReady(false)
        assertFalse(party.homeWatching)
        viewModel.onHomeReady(true)
        assertTrue(party.homeWatching)
        // MilO is put away.
        viewModel.onInFront(false)
        assertFalse(party.homeWatching)
    }

    // ---- On demand -------------------------------------------------------------------------------

    @Test
    fun `asked for on demand it plays once with its pop, on any day, and stores nothing`() =
        runTest {
            nowMs = at(2027, 9, 0, day = 20)
            val party = partyOn()
            val viewModel = screens(party)
            val storedBefore = settings.current()

            // Held on the What's new screen: Home is not on top yet.
            viewModel.onInFront(true)
            party.askFor()
            runCurrent()
            assertTrue(viewModel.asked.value)
            assertEquals(true, viewModel.hasTheScreen.value)
            assertNull(viewModel.showing.value)

            viewModel.onHomeReady(true)
            runCurrent()
            assertEquals(PartyShowing.ON_DEMAND, viewModel.showing.value)
            playThrough(viewModel)

            assertEquals(1, pops)
            assertNull(viewModel.showing.value)
            assertFalse(viewModel.asked.value)
            assertEquals(storedBefore, settings.current())
            assertTrue(log.entries.isEmpty())
        }

    @Test
    fun `on demand the pop is heard with both sounds switched off, as Settings' Play is`() =
        runTest {
            heard = false
            val party = partyOn()
            val viewModel = screens(party)
            viewModel.homeInFront()
            party.askFor()
            runCurrent()

            playThrough(viewModel)

            assertEquals(1, pops)
        }

    @Test
    fun `a party on demand that is cut short is not played again, and owes no pop`() = runTest {
        val party = partyOn()
        val viewModel = screens(party)
        viewModel.homeInFront()
        party.askFor()
        runCurrent()
        viewModel.onShown()
        runCurrent()

        viewModel.onHomeReady(false)
        viewModel.onDone(popped = false)
        runCurrent()
        viewModel.onHomeReady(true)
        runCurrent()

        assertNull(viewModel.showing.value)
        assertFalse(viewModel.asked.value)
        assertEquals(0, pops)
    }

    @Test
    fun `a request that something else kept the screen from is dropped`() = runTest {
        val party = partyOn()
        val viewModel = screens(party)
        viewModel.onInFront(true)
        party.askFor()
        runCurrent()

        // "Keep editing" on the question before a form is left.
        viewModel.onAskedDropped()
        runCurrent()
        viewModel.onHomeReady(true)
        runCurrent()

        assertNull(viewModel.showing.value)
        assertEquals(false, viewModel.hasTheScreen.value)
    }

    @Test
    fun `a party that was owed yesterday and is asked for today plays on demand`() = runTest {
        settings.setPartyOwedSinceMs(at(2027, 22, 15))
        nowMs = at(2027, 8, 0, day = 7)
        val party = partyOn()
        val viewModel = screens(party)
        viewModel.homeInFront()
        runCurrent()
        assertNull(viewModel.showing.value)

        party.askFor()
        runCurrent()

        assertEquals(PartyShowing.ON_DEMAND, viewModel.showing.value)
    }
}
