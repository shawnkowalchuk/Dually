package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.Trip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One logbook on every phone (ADR-017): what this phone holds of the other phones' trips. */
class CloudLogbookTest {
    private val minute = 60_000L

    private fun trip(
        id: Long,
        startMin: Long,
        endMin: Long,
        status: TripStatus = TripStatus.FINISHED,
    ) = Trip(
        id = id,
        startedAtMs = startMin * minute,
        endedAtMs = endMin * minute,
        status = status,
        startedBy = TripStartCause.MANUAL,
        truckSeen = true,
    )

    @Test
    fun twoRecordingsOfOneDriveAreTheSameDriveAndTwoDrivesAreNot() {
        assertTrue(sameDrive(trip(1, 0, 30), trip(2, 1, 29)))
        assertTrue(sameDrive(trip(1, 0, 30), trip(2, 10, 30)))
        assertFalse(sameDrive(trip(1, 0, 30), trip(2, 20, 60)))
        assertFalse(sameDrive(trip(1, 0, 30), trip(2, 31, 50)))
    }

    @Test
    fun anotherPhonesTripsComeInMarkedAndTheSameDriveIsLeftOut() {
        val own = listOf(trip(1, 0, 30))
        val others =
            OtherPhones(mapOf("iphone" to listOf(trip(7, 1, 29), trip(8, 60, 90))), mapOf())

        val change = logbookChange(stored = emptyList(), own = own, others = others)

        assertEquals(listOf(8L), change.insert.map { it.homeTripId })
        assertEquals("iphone", change.insert.single().homePhone)
        assertEquals(0L, change.insert.single().id, "numbered by this phone's table")
        assertTrue(change.update.isEmpty() && change.delete.isEmpty())
    }

    @Test
    fun aChangedTripIsUpdatedInPlaceAndAGoneOneIsRemoved() {
        val stored =
            listOf(
                trip(40, 60, 90).copy(homePhone = "iphone", homeTripId = 8),
                trip(41, 100, 120).copy(homePhone = "iphone", homeTripId = 9),
            )
        val others = OtherPhones(mapOf("iphone" to listOf(trip(8, 60, 95))), mapOf())

        val change = logbookChange(stored, own = emptyList(), others = others)

        assertEquals(listOf(40L), change.update.map { it.id })
        assertEquals(95 * minute, change.update.single().endedAtMs)
        assertEquals(listOf(41L), change.delete)
        assertTrue(change.insert.isEmpty())
    }

    @Test
    fun ofTwoOtherPhonesRecordingOneDriveTheOneThatStartedFirstIsKept() {
        val others =
            OtherPhones(
                mapOf("a" to listOf(trip(1, 5, 30)), "b" to listOf(trip(2, 4, 30))),
                mapOf(),
            )

        val change = logbookChange(emptyList(), emptyList(), others)

        assertEquals(listOf("b"), change.insert.map { it.homePhone })
    }

    @Test
    fun aPhoneIsAskedOnlyWhenBothSidesHaveTripsAndItHasNotAnswered() {
        val others = OtherPhones(mapOf("a" to listOf(trip(1, 0, 30))), mapOf())
        val none = OtherPhones(mapOf(), mapOf())
        assertTrue(cloudAsksToMerge(3, others, answered = null))
        assertTrue(cloudAsksToMerge(3, others, answered = false))
        assertFalse(cloudAsksToMerge(3, others, answered = true))
        assertFalse(cloudAsksToMerge(0, others, answered = null))
        assertFalse(cloudAsksToMerge(3, none, answered = null))
    }

    @Test
    fun aPhoneUploadsIntoItsOwnFolderWithItsRecord() {
        val snapshot =
            CloudSnapshot(listOf(CloudDocument("trips/1", "{}")), unknown = setOf(CLOUD_SETTINGS))
        val record = cloudPhoneDocument("p1", "iPhone", "iphone")

        val moved = snapshot.inFolder(cloudPhoneFolder("p1"), record)

        assertEquals(listOf("phones/p1/trips/1", "phones/p1"), moved.documents.map { it.path })
        assertEquals(setOf("phones/p1/backup/settings"), moved.unknown)
        assertEquals("iPhone", cloudPhoneOf(record.json)?.name)
    }
}
