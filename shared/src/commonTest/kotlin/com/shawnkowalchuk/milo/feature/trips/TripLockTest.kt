package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.time.testZone
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.Trip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What may be changed on a listed trip since ADR-017: any phone's trip, unless its month was
 * sent to the accountant (locked once sent) or it is in an older version's backup.
 */
class TripLockTest {
    private val zone = testZone("America/Edmonton")
    private val start = 1_763_100_000_000L

    private fun trip(id: Long, homePhone: String? = null) = Trip(
        id = id,
        startedAtMs = start + id * 3_600_000,
        endedAtMs = start + id * 3_600_000 + 1_200_000,
        status = TripStatus.FINISHED,
        startedBy = TripStartCause.MANUAL,
        truckSeen = true,
        distanceMetres = 5_000.0,
        homePhone = homePhone,
        homeTripId = homePhone?.let { id },
    )

    private fun lines(trips: List<Trip>, locked: Boolean) =
        monthSummary(trips, zone, false, null, null, null, DistanceUnit.KILOMETRES, locked)
            .days.flatMap { it.trips }.associateBy { it.id }

    @Test
    fun anotherPhonesTripMayBeChangedHereAndSaysWhereItCameFrom() {
        val line = lines(listOf(trip(1, homePhone = "0f9b6c1e")), locked = false).getValue(1)
        assertTrue(line.fromOtherPhone)
        assertFalse(line.readOnly)
        assertTrue(line.editable)
    }

    @Test
    fun aSentMonthIsLockedForEveryTripInIt() {
        val all = lines(listOf(trip(1), trip(2, homePhone = "0f9b6c1e")), locked = true)
        for (line in all.values) {
            assertTrue(line.readOnly)
            assertFalse(line.editable)
            assertEquals(emptyList(), line.markableAs)
        }
    }

    @Test
    fun aTripInAnOlderVersionsBackupIsReadOnly() {
        val line = lines(
            listOf(trip(1, homePhone = "earlier-backup:a1")),
            locked = false,
        ).getValue(1)
        assertTrue(line.readOnly)
        assertFalse(line.editable)
    }
}
