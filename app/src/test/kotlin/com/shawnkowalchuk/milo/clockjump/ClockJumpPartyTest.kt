package com.shawnkowalchuk.milo.clockjump

import com.shawnkowalchuk.milo.core.clock.DAY_MS
import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.core.party.PopAtTripStart
import com.shawnkowalchuk.milo.core.party.TripStart
import com.shawnkowalchuk.milo.core.party.partyOwedNow
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.party.OneYearParty
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

private fun at(year: Int, day: Int, hour: Int, minute: Int = 0): Long =
    LocalDateTime.of(year, 10, day, hour, minute).atZone(EDMONTON).toInstant().toEpochMilli()

/**
 * MilO's one-year party on a phone whose date is set a day ahead by hand for a few seconds
 * (ADR-006: Shawn does it for lives in a game, several times an evening). The party goes by the
 * day, so this is where the trick could do the most: a party a day early, or none on the day.
 * The real [OneYearParty] runs on MilO's real clock (`TrustedClock`, on a [JumpingPhone]).
 *
 * MilO's first recorded trip was on 6 October 2026, so the day is 6 October.
 */
class ClockJumpPartyTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val log = FakeEventLogDao()
    private val settings = SettingsStore(FakeSettingsFile())
    private val firstTrip = TripStart(at(2026, 6, 7, 42), recordedStartedAtMs = null, false)

    private fun partyOn(phone: JumpingPhone) = OneYearParty(
        tripStarts = { listOf(firstTrip) },
        settings = settings,
        eventLog = EventLogRepository(log),
        crashFileStore = CrashFileStore(folder.root),
        clock = { phone.clock.now() },
        zone = { EDMONTON },
    )

    @Test
    fun `the date set a day ahead on the eve brings no party a day early`() = runTest {
        val phone = JumpingPhone(at(2027, 5, 19, 30))
        val party = partyOn(phone)

        // The trick: the phone now says it is the evening of the 6th, the day of the party.
        phone.setAhead()
        phone.advance(4_000)
        assertEquals(at(2027, 6, 19, 30) + 4_000, phone.phoneNowMs)
        // A trip starts in those seconds. MilO goes by its own time: it is the 5th.
        assertEquals(PopAtTripStart.NOT_TODAY, party.atTripStart())
        phone.advance(8_000)
        phone.setBack()

        assertEquals(PopAtTripStart.NOT_TODAY, party.atTripStart())
        assertNull(settings.current().partyOwedSinceMs)
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `the date set a day ahead on the day takes no party away`() = runTest {
        val phone = JumpingPhone(at(2027, 6, 19, 30))
        val party = partyOn(phone)

        // The phone says it is the 7th for these seconds. It is the 6th.
        phone.setAhead()
        phone.advance(4_000)

        assertEquals(PopAtTripStart.AT_ONCE, party.atTripStart())
        // What Home owes is dated by MilO's time too, and is owed there and then.
        val owed = settings.current().partyOwedSinceMs
        assertEquals(at(2027, 6, 19, 30) + 4_000, owed)
        assertTrue(partyOwedNow(owed, phone.clock.now(), EDMONTON))
        phone.advance(8_000)
        phone.setBack()
        assertTrue(partyOwedNow(owed, phone.clock.now(), EDMONTON))
    }

    @Test
    fun `tricks up to midnight on the day neither end the day early nor stretch it`() = runTest {
        val phone = JumpingPhone(at(2027, 6, 23, 40))
        val party = partyOn(phone)

        // Five tricks, four minutes apart, as on the evening of 2026-10-07.
        for (trick in 1..5) {
            phone.setAhead()
            phone.advance(9_000)
            val before = phone.trueNowMs < at(2027, 7, 0)
            val expected = if (before) PopAtTripStart.AT_ONCE else PopAtTripStart.NOT_TODAY
            assertEquals("trick $trick", expected, party.atTripStart())
            phone.setBack()
            phone.advance(4 * 60_000L)
        }

        // It is 00:00:45 on the 7th now, by every clock.
        assertEquals(PopAtTripStart.NOT_TODAY, party.atTripStart())
        val owed = settings.current().partyOwedSinceMs
        assertFalse(partyOwedNow(owed, phone.clock.now(), EDMONTON))
    }

    @Test
    fun `a date that is really a day ahead and stays there is believed, ten minutes late`() =
        runTest {
            val phone = JumpingPhone(at(2027, 5, 9, 0))
            val party = partyOn(phone)
            phone.setAhead()

            // Watched every half minute, as MilO watches a clock it does not believe.
            repeat(19) {
                phone.advance(30_000)
                assertEquals(PopAtTripStart.NOT_TODAY, party.atTripStart())
            }
            phone.advance(60_000)

            assertEquals(PopAtTripStart.AT_ONCE, party.atTripStart())
        }

    @Test
    fun `a MilO that starts inside a jump with nothing to check the date against goes by it`() =
        runTest {
            // The gap ADR-006 leaves open: the first MilO process after the phone was switched
            // on, started in the very seconds the date is ahead, on the eve of the party.
            val phone = JumpingPhone(at(2027, 5, 19, 30), miloHasRun = false)
            phone.setAhead()
            phone.newProcessWithoutAnchor()
            val party = partyOn(phone)

            // For these seconds this MilO has only the phone's date, and a trip that starts in
            // them is taken to start on the 6th.
            assertEquals(PopAtTripStart.AT_ONCE, party.atTripStart())
            val owed = settings.current().partyOwedSinceMs

            // The date comes back, and MilO takes the phone's time at once (probation).
            phone.advance(9_000)
            phone.setBack()
            assertEquals(PopAtTripStart.NOT_TODAY, party.atTripStart())
            // What it stored lies a day ahead of now, and is not owed.
            assertEquals(at(2027, 5, 19, 30) + DAY_MS, owed)
            assertFalse(partyOwedNow(owed, phone.clock.now(), EDMONTON))
        }
}
