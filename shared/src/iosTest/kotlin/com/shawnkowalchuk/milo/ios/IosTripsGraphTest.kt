package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.dateOf
import com.shawnkowalchuk.milo.core.time.momentOf
import com.shawnkowalchuk.milo.core.time.systemZoneId
import com.shawnkowalchuk.milo.core.time.timeOf
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripCorrections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The graph of a year on the iPhone (2026-11-16), worked out by Android's rules: a trip in the
 * month it started in, months to come without a point, and the scale rounded as Android's.
 */
class IosTripsGraphTest {
    private val world = RecorderWorld()
    private val zone = systemZoneId()
    private val now = at(month = 11, day = 16)
    private val trips =
        IosTrips(
            trips = world.trips,
            corrections = TripCorrections(world.trips, world.log, { now }),
            eventLog = world.log,
            zone = { zone },
            locale = { Locale("en_CA") },
            clock = { now },
            openTripStart = { null },
            unit = { DistanceUnit.KILOMETRES },
        )

    @AfterTest
    fun cleanUp() = world.close()

    private fun at(month: Int, day: Int) =
        momentOf(dateOf(2026, month, day), timeOf(10, 0), zone, null)

    private suspend fun trip(startMs: Long, category: TripCategory, metres: Double) {
        world.tripDao().insert(
            Trip(
                startedAtMs = startMs,
                endedAtMs = startMs + 1_800_000,
                status = TripStatus.FINISHED,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = metres,
                category = category,
            ),
        )
    }

    @Test
    fun `each month adds up its own trips and the months to come have no point`() = runTest {
        trip(at(month = 3, day = 4), TripCategory.BUSINESS, 412_300.0)
        trip(at(month = 3, day = 9), TripCategory.PERSONAL, 88_000.0)
        trip(at(month = 11, day = 2), TripCategory.BUSINESS, 10_000.0)

        val graph = trips.yearGraph(2026)

        assertEquals(12, graph.months.size)
        assertEquals(10, graph.currentIndex, "November is picked when its year opens")
        assertFalse(graph.canStepForward)
        assertEquals(412.3, graph.months[2].business)
        assertEquals(88.0, graph.months[2].personal)
        assertEquals("March: Business 412.3 km · Personal 88.0 km", graph.months[2].line)
        assertTrue(graph.months[10].begun)
        assertFalse(graph.months[11].begun, "December has not begun")
        assertEquals(600.0, graph.scaleTop, "four round steps of 150 reach 412.3")
    }

    @Test
    fun `a year gone by can step forward and picks no month`() = runTest {
        val graph = trips.yearGraph(2025)

        assertTrue(graph.canStepForward)
        assertEquals(-1, graph.currentIndex)
        assertTrue(graph.months.all { it.begun })
        assertEquals(4.0, graph.scaleTop, "an empty year counts 0 to 4")
    }
}
