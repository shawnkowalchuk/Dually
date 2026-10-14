package com.shawnkowalchuk.milo.platform.party

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.core.party.PopAtTripStart
import com.shawnkowalchuk.milo.core.party.TripStart
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
    LocalDateTime.of(year, month, day, hour, minute).atZone(EDMONTON).toInstant().toEpochMilli()

/**
 * What the trip service is told at a trip's first sound, on the stored trips, a settings file
 * and an event log held in memory: on which days the pop is played, by whom, what is stored for
 * Home, and that nothing which goes wrong here reaches the trip.
 */
// currentTime, the test's own clock, is marked experimental by the coroutines library.
@OptIn(ExperimentalCoroutinesApi::class)
class OneYearPartyTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val trips = FakeTripDao()
    private val log = FakeEventLogDao()
    private val file = FakeSettingsFile()
    private val settings = SettingsStore(file)
    private var nowMs = at(2027, 10, 6, hour = 7, minute = 42)

    private fun partyOn(
        file: DataStore<Preferences> = this.file,
        tripStarts: suspend () -> List<TripStart> = TripRepository(trips)::findTripStarts,
        eventLog: EventLogDao = log,
    ) = OneYearParty(
        tripStarts = tripStarts,
        settings = SettingsStore(file),
        eventLog = EventLogRepository(eventLog),
        crashFileStore = CrashFileStore(folder.root),
        clock = { nowMs },
        zone = { EDMONTON },
    )

    private fun stored(
        startedAtMs: Long,
        status: TripStatus = TripStatus.FINISHED,
        addedByHand: Boolean = false,
        recordedStartedAtMs: Long? = null,
    ) {
        trips.rows +=
            Trip(
                id = trips.rows.size + 1L,
                startedAtMs = startedAtMs,
                endedAtMs = startedAtMs + 600_000L,
                status = status,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                addedByHand = addedByHand,
                recordedStartedAtMs = recordedStartedAtMs,
            )
    }

    /** A year of trips from 6 October 2026, and the one that is starting now, open. */
    private fun aYearOfTrips() {
        stored(at(2026, 10, 6, hour = 7, minute = 40))
        stored(at(2026, 10, 6, hour = 16))
        stored(at(2027, 3, 12))
        stored(nowMs, status = TripStatus.OPEN)
    }

    private fun lines(category: EventCategory) =
        log.entries.filter { it.category == category }.map { it.message }

    // ---- Every other day -------------------------------------------------------------------------

    @Test
    fun `on every other day a trip's start is told nothing, and nothing is stored or written`() =
        runTest {
            aYearOfTrips()
            val party = partyOn()
            party.homeWatching = true
            val days =
                listOf(
                    at(2026, 10, 6, hour = 18),
                    at(2026, 10, 7),
                    at(2027, 1, 1),
                    at(2027, 10, 5, hour = 23, minute = 59),
                    at(2027, 10, 7, hour = 0),
                    at(2028, 10, 5),
                )

            for (day in days) {
                nowMs = day
                assertEquals(PopAtTripStart.NOT_TODAY, party.atTripStart())
            }

            assertEquals(MiloSettings(), settings.current())
            assertTrue(log.entries.isEmpty())
            assertFalse(party.takePop())
        }

    @Test
    fun `a MilO with no trip yet, or only the one that is starting, has no party`() = runTest {
        val party = partyOn()
        assertEquals(PopAtTripStart.NOT_TODAY, party.atTripStart())

        stored(nowMs, status = TripStatus.OPEN)

        assertEquals(PopAtTripStart.NOT_TODAY, party.atTripStart())
        assertNull(settings.current().partyOwedSinceMs)
    }

    // ---- The day ---------------------------------------------------------------------------------

    @Test
    fun `on the day, with MilO not in front, the service plays the pop and Home owes the party`() =
        runTest {
            aYearOfTrips()
            val party = partyOn()

            assertEquals(PopAtTripStart.AT_ONCE, party.atTripStart())

            assertEquals(nowMs, settings.current().partyOwedSinceMs)
            assertEquals(nowMs, party.owedSinceMs.first())
            // Nothing was left for an animation to take.
            assertFalse(party.takePop())
            val line = lines(EventCategory.SERVICE).single()
            assertTrue(
                line,
                line.startsWith("Dually's party: a trip starts on the day of its first"),
            )
            assertTrue(line, "2026-10-06" in line)
            assertTrue(line, "The pop takes the place of this trip's first sound" in line)
        }

    @Test
    fun `on the day, with Home in front, the pop is left to the animation`() = runTest {
        aYearOfTrips()
        val party = partyOn()
        party.homeWatching = true

        assertEquals(PopAtTripStart.WITH_THE_ANIMATION, party.atTripStart())

        assertEquals(nowMs, settings.current().partyOwedSinceMs)
        assertTrue(party.takePop())
        assertFalse(party.takePop())
        assertTrue("Home is in front" in lines(EventCategory.SERVICE).single())
    }

    @Test
    fun `every trip of the day is told so, and the newest is what Home owes`() = runTest {
        aYearOfTrips()
        val party = partyOn()

        assertEquals(PopAtTripStart.AT_ONCE, party.atTripStart())
        nowMs = at(2027, 10, 6, hour = 13)
        assertEquals(PopAtTripStart.AT_ONCE, party.atTripStart())
        nowMs = at(2027, 10, 6, hour = 23, minute = 59)
        assertEquals(PopAtTripStart.AT_ONCE, party.atTripStart())

        assertEquals(nowMs, settings.current().partyOwedSinceMs)
        assertEquals(3, lines(EventCategory.SERVICE).size)
    }

    @Test
    fun `it comes again the year after, and the year after that`() = runTest {
        aYearOfTrips()
        val party = partyOn()

        for (year in 2028..2031) {
            nowMs = at(year, 10, 6, hour = 8)
            assertEquals("$year", PopAtTripStart.AT_ONCE, party.atTripStart())
        }
    }

    @Test
    fun `the party that was shown is owed no longer, and a trip since then still is`() = runTest {
        aYearOfTrips()
        val party = partyOn()
        party.atTripStart()
        val first = nowMs

        party.shown(forMs = first)
        assertNull(settings.current().partyOwedSinceMs)

        nowMs = at(2027, 10, 6, hour = 9)
        party.atTripStart()
        // The first party's showing is stored late: the second trip's stays.
        party.shown(forMs = first)
        assertEquals(nowMs, settings.current().partyOwedSinceMs)
    }

    // ---- Which trips it counts from --------------------------------------------------------------

    @Test
    fun `discarded and deleted trips count as days MilO was in use, whatever became of them`() =
        runTest {
            // Day one was a false start and a trip he deleted; the first trip kept is a week on.
            stored(at(2026, 10, 6, hour = 7), status = TripStatus.DISCARDED)
            stored(at(2026, 10, 6, hour = 8), status = TripStatus.DELETED)
            stored(at(2026, 10, 13))

            assertEquals(PopAtTripStart.AT_ONCE, partyOn().atTripStart())
            nowMs = at(2027, 10, 13)
            assertEquals(PopAtTripStart.NOT_TODAY, partyOn().atTripStart())
        }

    @Test
    fun `a trip typed in by hand for a day before MilO does not move the party`() = runTest {
        aYearOfTrips()
        stored(at(2025, 6, 1), addedByHand = true)

        assertEquals(PopAtTripStart.AT_ONCE, partyOn().atTripStart())
        nowMs = at(2028, 6, 1)
        assertEquals(PopAtTripStart.NOT_TODAY, partyOn().atTripStart())
    }

    @Test
    fun `the first trip moved to another day by hand still counts by the day it was recorded`() =
        runTest {
            stored(at(2026, 10, 9), recordedStartedAtMs = at(2026, 10, 6, hour = 7, minute = 40))
            stored(at(2026, 10, 12))

            assertEquals(PopAtTripStart.AT_ONCE, partyOn().atTripStart())
            nowMs = at(2027, 10, 9)
            assertEquals(PopAtTripStart.NOT_TODAY, partyOn().atTripStart())
        }

    @Test
    fun `it travels with an import, the trips of another phone bring their day along`() = runTest {
        aYearOfTrips()
        val party = partyOn()
        assertEquals(PopAtTripStart.AT_ONCE, party.atTripStart())

        // An import replaces every trip: the file's first trip was on 2 March 2026.
        trips.rows.clear()
        stored(at(2026, 3, 2))
        stored(at(2027, 2, 20))

        assertEquals(PopAtTripStart.NOT_TODAY, party.atTripStart())
        nowMs = at(2028, 3, 2)
        assertEquals(PopAtTripStart.AT_ONCE, party.atTripStart())
    }

    // ---- Nothing that goes wrong here reaches the trip -------------------------------------------

    @Test
    fun `trips that cannot be read leave the trip its usual sounds, and the Log says why`() =
        runTest {
            val party = partyOn(tripStarts = { throw IllegalStateException("it is shut") })
            party.homeWatching = true

            assertEquals(PopAtTripStart.NOT_TODAY, party.atTripStart())

            assertEquals(
                listOf("Dually could not tell whether today is the day of its party"),
                lines(EventCategory.ERROR),
            )
            assertNull(settings.current().partyOwedSinceMs)
            assertFalse(party.takePop())
        }

    @Test
    fun `trips that take too long to read hold the trip's sound back one second and no more`() =
        runTest {
            val never = CompletableDeferred<List<TripStart>>()
            val party = partyOn(tripStarts = { never.await() })

            val began = currentTime
            assertEquals(PopAtTripStart.NOT_TODAY, party.atTripStart())

            assertEquals(1_000L, currentTime - began)
            val line = lines(EventCategory.ERROR).single()
            assertTrue(line, line.startsWith("Dually could not tell in time"))
        }

    @Test
    fun `a party that cannot be stored is not left to the animation, the service plays the pop`() =
        runTest {
            aYearOfTrips()
            val party = partyOn(file = UnreadableSettingsFile)
            party.homeWatching = true

            assertEquals(PopAtTripStart.AT_ONCE, party.atTripStart())

            assertFalse(party.takePop())
            assertEquals(
                listOf("Dually could not store that its mascot's party is owed"),
                lines(EventCategory.ERROR),
            )
            // And Home, which cannot read the file either, is owed nothing.
            assertNull(party.owedSinceMs.first())
        }

    @Test
    fun `an event log that cannot be written changes nothing, and the line goes to a crash file`() =
        runTest {
            aYearOfTrips()
            val brokenLog =
                object : EventLogDao by log {
                    override suspend fun insert(entry: EventLogEntry): Long =
                        throw IOException("the disk is full")
                }
            val party = partyOn(eventLog = brokenLog)

            assertEquals(PopAtTripStart.AT_ONCE, party.atTripStart())

            assertEquals(nowMs, settings.current().partyOwedSinceMs)
            val crashFiles = CrashFileStore(folder.root)
            val record = crashFiles.read(crashFiles.pendingFiles().single())
            assertTrue(record.summary, "Dually's party: a trip starts on the day" in record.summary)
        }

    @Test
    fun `a showing that cannot be stored is one line in the Log`() = runTest {
        val party = partyOn(file = UnreadableSettingsFile)

        party.shown(forMs = nowMs)

        assertEquals(
            listOf("Dually could not store that its mascot's party has been shown"),
            lines(EventCategory.ERROR),
        )
    }

    // ---- On demand -------------------------------------------------------------------------------

    @Test
    fun `asked for on demand it stays asked for until Home has dealt with it`() = runTest {
        val party = partyOn()
        assertFalse(party.askedFor.value)

        party.askFor()
        assertTrue(party.askedFor.value)
        party.askedDealtWith()

        assertFalse(party.askedFor.value)
        assertEquals(MiloSettings(), settings.current())
        assertTrue(log.entries.isEmpty())
    }
}
