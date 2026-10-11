package com.shawnkowalchuk.milo.core.party

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

private fun onTheDay(hour: Int, minute: Int = 0, day: Int = 6): Long =
    LocalDateTime.of(2027, 10, day, hour, minute).atZone(EDMONTON).toInstant().toEpochMilli()

/** Who plays the party's pop at a trip's start, and what Home still owes afterwards. */
class PartyPopTest {
    // ---- Who plays the pop -----------------------------------------------------------------------

    @Test
    fun `on any other day the trip's sounds are left alone, wherever MilO is`() {
        val inTheBackground = popAtTripStart(partyDay = false, homeWatching = false)
        val withHomeInFront = popAtTripStart(partyDay = false, homeWatching = true)

        assertEquals(PopAtTripStart.NOT_TODAY, inTheBackground)
        assertEquals(PopAtTripStart.NOT_TODAY, withHomeInFront)
    }

    @Test
    fun `on the day the trip service plays the pop while Home is not in front`() {
        assertEquals(PopAtTripStart.AT_ONCE, popAtTripStart(partyDay = true, homeWatching = false))
    }

    @Test
    fun `on the day the pop is the popper's while Home is in front`() {
        val pop = popAtTripStart(partyDay = true, homeWatching = true)

        assertEquals(PopAtTripStart.WITH_THE_ANIMATION, pop)
    }

    // ---- The pop on its way to the animation -----------------------------------------------------

    @Test
    fun `a pop that was left is taken once, by whoever comes first`() {
        val handover = PopHandover()
        handover.leave()

        // The animation, as the mascot is first drawn...
        assertTrue(handover.take())
        // ...and the trip service a moment later finds nothing to play.
        assertFalse(handover.take())
    }

    @Test
    fun `nothing can be taken that was not left`() {
        assertFalse(PopHandover().take())
    }

    @Test
    fun `two trips that leave their pop before either is taken leave one`() {
        val handover = PopHandover()
        handover.leave()
        handover.leave()

        assertTrue(handover.take())
        assertFalse(handover.take())
    }

    @Test
    fun `a pop that was taken can be left again by the next trip`() {
        val handover = PopHandover()
        handover.leave()
        handover.take()

        handover.leave()

        assertTrue(handover.take())
    }

    // ---- What Home owes --------------------------------------------------------------------------

    @Test
    fun `nothing stored, nothing owed`() {
        assertFalse(partyOwedNow(owedSinceMs = null, nowMs = onTheDay(9), zone = EDMONTON))
    }

    @Test
    fun `a party is owed from the trip's start for the rest of that day`() {
        val tripStarted = onTheDay(7, 42)

        assertTrue(partyOwedNow(tripStarted, tripStarted, EDMONTON))
        assertTrue(partyOwedNow(tripStarted, onTheDay(7, 43), EDMONTON))
        assertTrue(partyOwedNow(tripStarted, onTheDay(23, 59), EDMONTON))
    }

    @Test
    fun `after midnight nothing left over is owed`() {
        val tripStarted = onTheDay(22, 15)

        assertTrue(partyOwedNow(tripStarted, onTheDay(0, 0, day = 7) - 1, EDMONTON))
        assertFalse(partyOwedNow(tripStarted, onTheDay(0, 0, day = 7), EDMONTON))
        assertFalse(partyOwedNow(tripStarted, onTheDay(8, 0, day = 7), EDMONTON))
        // Nor a year later, should it still be in the settings then.
        val nextYear = LocalDateTime.of(2028, 10, 6, 8, 0).atZone(EDMONTON).toInstant()
        assertFalse(partyOwedNow(tripStarted, nextYear.toEpochMilli(), EDMONTON))
    }

    @Test
    fun `a moment that lies ahead of now is not owed`() {
        // Stored under a date that was a day ahead, or brought along by a backup.
        val tomorrow = onTheDay(7, 42, day = 7)

        assertFalse(partyOwedNow(tomorrow, onTheDay(7, 43), EDMONTON))
        // Later the same day it would still be ahead by seconds: not believed either.
        assertFalse(partyOwedNow(onTheDay(7, 45), onTheDay(7, 44), EDMONTON))
    }

    @Test
    fun `whether it is still the day is read in the zone the phone is in now`() {
        // 22:30 in Edmonton is half past midnight in Toronto, the day after.
        val tripStarted = onTheDay(21, 30)
        val anHourLater = onTheDay(22, 30)

        assertTrue(partyOwedNow(tripStarted, anHourLater, EDMONTON))
        assertFalse(partyOwedNow(tripStarted, anHourLater, ZoneId.of("America/Toronto")))
    }
}
