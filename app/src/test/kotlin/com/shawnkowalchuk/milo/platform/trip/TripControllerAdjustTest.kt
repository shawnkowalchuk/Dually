package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.setDistanceAdjustSteps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The distance adjustment where a trip is recorded: the running figure and the stored one are
 * adjusted, and the trip rules still go by the distance that was measured.
 */
// runCurrent() is how a test lets the controller's worker run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerAdjustTest {
    private val world = FakeWorld()

    @Test
    fun `Settings at +5 percent stores and shows the distance 7 percent over the measured`() =
        runTest {
            world.settings.setDistanceAdjustSteps(10)
            val (controller, _) = recordingManualTrip(world)
            drive(world, controller, fixCount = 11, metresPerFix = 100.0)

            assertEquals(1_070.0, controller.activity.value.trip?.distanceMetres ?: 0.0, 1.0)

            controller.onTrigger(TripTrigger.MANUAL_END, "End button")
            runCurrent()

            assertEquals(1_070.0, world.trips.rows.single().distanceMetres, 1.0)
            // The Log keeps the distance that was measured.
            assertTrue(
                world.logged(EventCategory.TRIP).last().startsWith("Trip 1: finished, 1000 m"),
            )
        }

    @Test
    fun `Settings at -2 percent takes back what is built in`() = runTest {
        world.settings.setDistanceAdjustSteps(-4)
        val (controller, _) = recordingManualTrip(world)
        drive(world, controller, fixCount = 11, metresPerFix = 100.0)

        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()

        assertEquals(1_000.0, world.trips.rows.single().distanceMetres, 1.0)
    }

    @Test
    fun `a trip under the minimum stays discarded, whatever the adjustment would make of it`() =
        runTest {
            world.settings.setDistanceAdjustSteps(10)
            val (controller, _) = recordingManualTrip(world)
            // 290 m measured: 310 m adjusted, over the minimum of 300 m.
            drive(world, controller, fixCount = 3, metresPerFix = 145.0)

            controller.onTrigger(TripTrigger.MANUAL_END, "End button")
            runCurrent()

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.DISCARDED, trip.status)
            assertEquals(310.3, trip.distanceMetres, 1.0)
            assertTrue(
                world.logged(EventCategory.TRIP).last().contains("290 m is under the minimum"),
            )
        }
}
