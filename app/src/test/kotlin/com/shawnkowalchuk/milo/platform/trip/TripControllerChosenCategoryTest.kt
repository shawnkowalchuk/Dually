package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.settings.setRunningTripCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Business or Personal chosen on Home while the trip is recorded (2026-10-16): the trip is saved
 * as the choice, set by hand, and the choice is taken out. The world's clock starts on a
 * Saturday, outside the work hours, so the hours alone make a trip Personal.
 */
// runCurrent() is how a test lets the controller's worker run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerChosenCategoryTest {
    private val world = FakeWorld()

    @Test
    fun `without a choice the hours decide, as before`() = runTest {
        val (controller, _) = recordingManualTrip(world)
        drive(world, controller, fixCount = 11, metresPerFix = 100.0)

        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()

        val trip = world.trips.rows.single()
        assertEquals(TripCategory.PERSONAL, trip.category)
        assertFalse(trip.categorySetByHand)
    }

    @Test
    fun `a choice for the trip wins, is set by hand, and is taken out at the end`() = runTest {
        val (controller, _) = recordingManualTrip(world)
        drive(world, controller, fixCount = 11, metresPerFix = 100.0)
        val tripId = world.openTrips.single().id
        world.settings.setRunningTripCategory(tripId, TripCategory.BUSINESS)

        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()

        val trip = world.trips.rows.single()
        assertEquals(TripStatus.FINISHED, trip.status)
        assertEquals(TripCategory.BUSINESS, trip.category)
        assertTrue(trip.categorySetByHand)
        assertNull(world.settings.current().runningTripCategory)
    }

    @Test
    fun `a chosen trip is never ignored, whatever the setting for trips outside the hours`() =
        runTest {
            world.settings.setIgnoreTripsOutsideSchedule(true)
            val (controller, _) = recordingManualTrip(world)
            drive(world, controller, fixCount = 11, metresPerFix = 100.0)
            world.settings.setRunningTripCategory(
                world.openTrips.single().id,
                TripCategory.PERSONAL,
            )

            controller.onTrigger(TripTrigger.MANUAL_END, "End button")
            runCurrent()

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(TripCategory.PERSONAL, trip.category)
            assertFalse(trip.ignoredOutsideSchedule)
        }

    @Test
    fun `a choice left over for another trip changes nothing, and is taken out`() = runTest {
        world.settings.setRunningTripCategory(999, TripCategory.BUSINESS)
        val (controller, _) = recordingManualTrip(world)
        drive(world, controller, fixCount = 11, metresPerFix = 100.0)

        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()

        assertEquals(TripCategory.PERSONAL, world.trips.rows.single().category)
        assertNull(world.settings.current().runningTripCategory)
    }
}
