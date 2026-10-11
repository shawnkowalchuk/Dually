package com.shawnkowalchuk.milo.core.party

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")
private val TORONTO: ZoneId = ZoneId.of("America/Toronto")

/** A moment by the clock on the wall in [zone]. */
private fun at(
    year: Int,
    month: Int,
    day: Int,
    hour: Int = 12,
    minute: Int = 0,
    zone: ZoneId = EDMONTON,
): Long = LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

/** A trip MilO recorded itself, never edited. */
private fun recorded(startedAtMs: Long) =
    TripStart(startedAtMs, recordedStartedAtMs = null, addedByHand = false)

/**
 * The day of MilO's one-year party: which stored trip it counts from, and which days are the
 * day. Shawn's first trips were on 6 October 2026, in Edmonton.
 */
class PartyDayTest {
    private val firstTrip = at(2026, 10, 6, hour = 7, minute = 42)

    // ---- Which trip it counts from ---------------------------------------------------------------

    @Test
    fun `with no trip stored there is no first trip, and no party on any day`() {
        assertNull(firstRecordedStartMs(emptyList()))
        assertFalse(isPartyDay(null, at(2027, 10, 6), EDMONTON))
    }

    @Test
    fun `it counts from the earliest trip MilO recorded, in whatever order they are stored`() {
        val trips = listOf(recorded(at(2026, 10, 9)), recorded(firstTrip), recorded(at(2027, 3, 1)))

        assertEquals(firstTrip, firstRecordedStartMs(trips))
    }

    @Test
    fun `a trip typed in by hand does not count, however early he dated it`() {
        val typedIn = TripStart(at(2025, 5, 20), recordedStartedAtMs = null, addedByHand = true)

        assertEquals(firstTrip, firstRecordedStartMs(listOf(typedIn, recorded(firstTrip))))
        // Nothing but trips typed in: MilO has recorded none.
        assertNull(firstRecordedStartMs(listOf(typedIn)))
    }

    @Test
    fun `a trip whose times he changed counts by the start MilO recorded`() {
        // He moved the first trip to the day before by hand, and a later one to a year earlier.
        val movedEarlier = TripStart(at(2026, 10, 5), recordedStartedAtMs = firstTrip, false)
        val slipOfTheYear = TripStart(at(2025, 10, 9), recordedStartedAtMs = at(2026, 10, 9), false)

        assertEquals(firstTrip, firstRecordedStartMs(listOf(slipOfTheYear, movedEarlier)))
    }

    // ---- The day ---------------------------------------------------------------------------------

    @Test
    fun `the day is the first trip's day of the year, one year on`() {
        assertTrue(isPartyDay(firstTrip, at(2027, 10, 6), EDMONTON))
        assertFalse(isPartyDay(firstTrip, at(2027, 10, 5), EDMONTON))
        assertFalse(isPartyDay(firstTrip, at(2027, 10, 7), EDMONTON))
        assertFalse(isPartyDay(firstTrip, at(2027, 9, 6), EDMONTON))
        assertFalse(isPartyDay(firstTrip, at(2027, 11, 6), EDMONTON))
    }

    @Test
    fun `it comes every year on that date`() {
        for (year in 2027..2040) {
            assertTrue("$year", isPartyDay(firstTrip, at(year, 10, 6), EDMONTON))
        }
    }

    @Test
    fun `the first trip's own day is not the day, and no day before it is`() {
        assertFalse(isPartyDay(firstTrip, firstTrip, EDMONTON))
        assertFalse(isPartyDay(firstTrip, at(2026, 10, 6, hour = 23), EDMONTON))
        // A phone whose date was really set back to before MilO's first trip.
        assertFalse(isPartyDay(firstTrip, at(2025, 10, 6), EDMONTON))
        assertFalse(isPartyDay(firstTrip, at(2020, 10, 6), EDMONTON))
    }

    @Test
    fun `it is the whole day, from midnight to midnight, whatever time the first trip started`() {
        assertFalse(isPartyDay(firstTrip, at(2027, 10, 6) - 12 * 3_600_000L - 1, EDMONTON))
        assertTrue(isPartyDay(firstTrip, at(2027, 10, 6, hour = 0), EDMONTON))
        assertTrue(isPartyDay(firstTrip, at(2027, 10, 6, hour = 23, minute = 59), EDMONTON))
        assertTrue(isPartyDay(firstTrip, at(2027, 10, 7, hour = 0) - 1, EDMONTON))
        assertFalse(isPartyDay(firstTrip, at(2027, 10, 7, hour = 0), EDMONTON))
        // A first trip a minute before midnight has the same day.
        val lateTrip = at(2026, 10, 6, hour = 23, minute = 59)
        assertTrue(isPartyDay(lateTrip, at(2027, 10, 6, hour = 0), EDMONTON))
        assertFalse(isPartyDay(lateTrip, at(2027, 10, 7, hour = 0), EDMONTON))
    }

    // ---- 29 February -----------------------------------------------------------------------------

    @Test
    fun `a first trip on 29 February is celebrated on 28 February in a year without one`() {
        val leapDay = at(2028, 2, 29)

        assertTrue(isPartyDay(leapDay, at(2029, 2, 28), EDMONTON))
        assertFalse(isPartyDay(leapDay, at(2029, 3, 1), EDMONTON))
        assertFalse(isPartyDay(leapDay, at(2029, 2, 27), EDMONTON))
        assertTrue(isPartyDay(leapDay, at(2030, 2, 28), EDMONTON))
        assertTrue(isPartyDay(leapDay, at(2031, 2, 28), EDMONTON))
    }

    @Test
    fun `and on 29 February, not the 28th, in a year that has one`() {
        val leapDay = at(2028, 2, 29)

        assertTrue(isPartyDay(leapDay, at(2032, 2, 29), EDMONTON))
        assertFalse(isPartyDay(leapDay, at(2032, 2, 28), EDMONTON))
        assertFalse(isPartyDay(leapDay, at(2032, 3, 1), EDMONTON))
    }

    @Test
    fun `a first trip on 28 February is not moved by a leap year`() {
        val lastOfFebruary = at(2027, 2, 28)

        assertTrue(isPartyDay(lastOfFebruary, at(2028, 2, 28), EDMONTON))
        assertFalse(isPartyDay(lastOfFebruary, at(2028, 2, 29), EDMONTON))
    }

    // ---- The zone the phone is in ----------------------------------------------------------------

    @Test
    fun `both days are read in the zone the phone is in now`() {
        // 23:30 on 6 October in Edmonton is 01:30 on 7 October in Toronto.
        val lateInEdmonton = at(2026, 10, 6, hour = 23, minute = 30)

        // At home the party is on the 6th.
        assertTrue(isPartyDay(lateInEdmonton, at(2027, 10, 6), EDMONTON))
        assertFalse(isPartyDay(lateInEdmonton, at(2027, 10, 7), EDMONTON))
        // With the phone in Toronto that trip is shown under the 7th, and so is the party.
        assertFalse(isPartyDay(lateInEdmonton, at(2027, 10, 6, zone = TORONTO), TORONTO))
        assertTrue(isPartyDay(lateInEdmonton, at(2027, 10, 7, zone = TORONTO), TORONTO))
    }

    @Test
    fun `a moment is on the day or not by the zone's own midnight`() {
        // 23:00 on the 6th in Edmonton is already the 7th in Toronto.
        val lateEvening = at(2027, 10, 6, hour = 23)

        assertTrue(isPartyDay(firstTrip, lateEvening, EDMONTON))
        assertFalse(isPartyDay(firstTrip, lateEvening, TORONTO))
        // 23:30 on the 5th in Edmonton: not yet the day there, but 01:30 on the 6th in Toronto.
        val eveBefore = at(2027, 10, 5, hour = 23, minute = 30)
        assertFalse(isPartyDay(firstTrip, eveBefore, EDMONTON))
        assertTrue(isPartyDay(firstTrip, eveBefore, TORONTO))
    }

    @Test
    fun `a day that is an hour short or an hour long is still one day`() {
        // The clocks go forward on 14 March 2027 in Edmonton and back on 7 November 2027.
        val spring = at(2026, 3, 14)
        val autumn = at(2026, 11, 7)

        assertTrue(isPartyDay(spring, at(2027, 3, 14, hour = 0), EDMONTON))
        assertTrue(isPartyDay(spring, at(2027, 3, 14, hour = 23, minute = 59), EDMONTON))
        assertFalse(isPartyDay(spring, at(2027, 3, 15, hour = 0), EDMONTON))
        assertTrue(isPartyDay(autumn, at(2027, 11, 7, hour = 0), EDMONTON))
        assertTrue(isPartyDay(autumn, at(2027, 11, 7, hour = 23, minute = 59), EDMONTON))
        assertFalse(isPartyDay(autumn, at(2027, 11, 8, hour = 0), EDMONTON))
    }
}
