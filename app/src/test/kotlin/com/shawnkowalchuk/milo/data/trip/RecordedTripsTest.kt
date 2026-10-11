package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which trips are "recorded trips", the number Google Play is asked for its review card by
 * (`core/review`): the trips MilO recorded itself that count on the Trips screen.
 */
class RecordedTripsTest {
    private fun trip(
        status: TripStatus,
        addedByHand: Boolean = false,
        editedByHand: Boolean = false,
        category: TripCategory? = TripCategory.BUSINESS,
        startedBy: TripStartCause = TripStartCause.TRUCK,
    ) = Trip(
        startedAtMs = 1_791_000_000_000L,
        endedAtMs = 1_791_000_900_000L.takeIf { status != TripStatus.OPEN },
        status = status,
        startedBy = startedBy,
        truckSeen = startedBy == TripStartCause.TRUCK,
        category = category.takeIf { status != TripStatus.OPEN },
        addedByHand = addedByHand,
        editedByHand = editedByHand,
    )

    @Test
    fun `a finished trip MilO recorded counts, Business or Personal, by the truck or the button`() {
        assertTrue(trip(TripStatus.FINISHED).isRecordedTrip)
        assertTrue(trip(TripStatus.FINISHED, category = TripCategory.PERSONAL).isRecordedTrip)
        assertTrue(trip(TripStatus.FINISHED, category = null).isRecordedTrip)
        assertTrue(trip(TripStatus.FINISHED, startedBy = TripStartCause.MANUAL).isRecordedTrip)
    }

    @Test
    fun `a recorded trip whose figures were edited still counts`() {
        assertTrue(trip(TripStatus.FINISHED, editedByHand = true).isRecordedTrip)
    }

    @Test
    fun `a trip being recorded, a discarded one and a deleted one do not count`() {
        for (status in TripStatus.entries.filter { it != TripStatus.FINISHED }) {
            assertFalse("$status", trip(status).isRecordedTrip)
        }
    }

    @Test
    fun `a trip typed in by hand does not count, for MilO missed it`() {
        assertFalse(trip(TripStatus.FINISHED, addedByHand = true).isRecordedTrip)
    }

    @Test
    fun `the count the storage gives is the count of the rule`() = runTest {
        val dao = FakeTripDao()
        val rows =
            TripStatus.entries.flatMap { status ->
                listOf(
                    trip(status),
                    trip(status, addedByHand = true),
                    trip(status, editedByHand = true),
                )
            } + trip(TripStatus.FINISHED, category = TripCategory.PERSONAL)
        rows.forEach { dao.insert(it) }

        val counted = TripRepository(dao).observeRecordedTripCount().first()

        assertEquals(dao.rows.count { it.isRecordedTrip }, counted)
        assertEquals(3, counted)
    }

    @Test
    fun `a deleted trip that is restored counts again, and a trip deleted counts no longer`() =
        runTest {
            val dao = FakeTripDao()
            val trips = TripRepository(dao)
            repeat(5) { dao.insert(trip(TripStatus.FINISHED)) }
            assertEquals(5, trips.observeRecordedTripCount().first())

            dao.changeStatus(3, TripStatus.FINISHED, TripStatus.DELETED)
            assertEquals(4, trips.observeRecordedTripCount().first())

            dao.changeStatus(3, TripStatus.DELETED, TripStatus.FINISHED)
            assertEquals(5, trips.observeRecordedTripCount().first())
        }
}
