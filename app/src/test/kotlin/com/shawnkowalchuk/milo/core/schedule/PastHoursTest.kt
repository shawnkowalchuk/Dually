package com.shawnkowalchuk.milo.core.schedule

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the work hours end for a trip, and which finished trips are asked about. */
class PastHoursTest {
    private val zone = ZoneOffset.UTC

    private fun at(text: String): Long = LocalDateTime.parse(text).toInstant(zone).toEpochMilli()

    @Test
    fun `a trip that starts five minutes before the end has the end of that day's hours`() {
        // Monday, with the hours as they come: 08:00 to 16:30.
        assertEquals(
            at("2026-11-02T16:30:00"),
            workHoursEndMs(at("2026-11-02T16:25:00"), DEFAULT_WORK_SCHEDULE, zone),
        )
    }

    @Test
    fun `a trip the hours make Personal has none`() {
        assertNull(workHoursEndMs(at("2026-11-02T16:30:00"), DEFAULT_WORK_SCHEDULE, zone))
        assertNull(workHoursEndMs(at("2026-11-02T07:55:00"), DEFAULT_WORK_SCHEDULE, zone))
        // Saturday is not tracked.
        assertNull(workHoursEndMs(at("2026-11-07T10:00:00"), DEFAULT_WORK_SCHEDULE, zone))
    }

    @Test
    fun `the filing still flags the same trips`() {
        val filed =
            classifyTrip(
                at("2026-11-02T16:25:00"),
                at("2026-11-02T17:06:00"),
                DEFAULT_WORK_SCHEDULE,
                zone,
            )
        assertEquals(TripClassification(TripCategory.BUSINESS, ranPastSchedule = true), filed)
    }

    @Test
    fun `only a Business trip past the hours that nobody chose is asked about`() {
        assertTrue(
            asksAboutPastHours(TripCategory.BUSINESS, ranPastSchedule = true, setByHand = false),
        )
        assertFalse(
            asksAboutPastHours(TripCategory.BUSINESS, ranPastSchedule = true, setByHand = true),
        )
        assertFalse(
            asksAboutPastHours(TripCategory.BUSINESS, ranPastSchedule = false, setByHand = false),
        )
        assertFalse(
            asksAboutPastHours(TripCategory.PERSONAL, ranPastSchedule = false, setByHand = false),
        )
        assertFalse(asksAboutPastHours(null, ranPastSchedule = false, setByHand = false))
    }
}
