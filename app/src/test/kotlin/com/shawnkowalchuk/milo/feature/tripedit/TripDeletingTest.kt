package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Delete on the edit screen (2026-11-03), against the stand-in trips table. */
class TripDeletingTest : TripEditingFixture() {
    @Test
    fun `a finished trip is deleted, and the log says it was on the edit screen`() = runTest {
        val trip = recorded()

        assertTrue(editing.delete(trip.id))

        assertEquals(TripStatus.DELETED, row(trip.id).status)
        assertTrue(
            logged(EventCategory.TRIP).last().startsWith(
                "Trip ${trip.id}: deleted on the edit screen",
            ),
        )
    }

    @Test
    fun `a trip being recorded is not deleted from here`() = runTest {
        val trip = recorded(TripStatus.OPEN)

        assertFalse(editing.delete(trip.id))

        assertEquals(TripStatus.OPEN, row(trip.id).status)
    }
}
