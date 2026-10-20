package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.GPS_AFTER_VEHICLE_REPORT_MS
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two safety nets beside a parked, connected truck (2026-10-19). On that day the phone's
 * Bluetooth had reported the truck connected for three days after it had gone; MilO waited beside
 * it with GPS off, and the phone's motion sensor reported neither drive. Android Auto connecting,
 * and the phone leaving the parked place (Android's geofence), now turn GPS on as that report
 * does, and the fixes decide.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerSafetyNetTest {
    private val world = FakeWorld().apply { motionSensorWatching = true }

    /** Three hours after the wait began: GPS has long been off. */
    private val threeHoursOn = STOPPED_AT_SECOND + 600 + 3 * 3_600

    @Test
    fun `Android Auto connecting hours into the wait reads GPS for ten minutes, and says so`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()
            world.nowMs = scene.timeOf(threeHoursOn)

            scene.controller.onAndroidAuto(connected = true, source = "Android Auto")
            runCurrent()

            assertEquals(world.nowMs + GPS_AFTER_VEHICLE_REPORT_MS, scene.service.fastGpsUntilMs)
            assertEquals(
                "Android Auto connected 0 s ago: the parked truck's position is read every 5 s " +
                    "for the next 10 min, and a trip starts if it drives off",
                world.logged(EventCategory.LOCATION).last(),
            )
            // It starts no trip by itself.
            assertEquals(1, world.trips.rows.size)
        }

    @Test
    fun `Android Auto going away is no sign`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        world.nowMs = scene.timeOf(threeHoursOn)

        scene.controller.onAndroidAuto(connected = false, source = "Android Auto")
        runCurrent()

        assertNull(scene.service.fastGpsUntilMs)
    }

    @Test
    fun `leaving the parked place hours into the wait reads GPS for ten minutes, and says so`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()
            world.nowMs = scene.timeOf(threeHoursOn)

            scene.controller.onLeftParkedPlace(world.nowMs - 90_000)
            runCurrent()

            assertEquals(
                world.nowMs - 90_000 + GPS_AFTER_VEHICLE_REPORT_MS,
                scene.service.fastGpsUntilMs,
            )
            assertEquals(
                "The phone left the parked truck's place 90 s ago (Android's geofence, 150 m): " +
                    "the parked truck's position is read every 5 s for the next 9 min, and a " +
                    "trip starts if it drives off",
                world.logged(EventCategory.LOCATION).last(),
            )
            assertEquals(1, world.trips.rows.size)
        }

    @Test
    fun `the drive the geofence announces starts its trip where the truck was parked`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        world.nowMs = scene.timeOf(threeHoursOn)
        scene.controller.onLeftParkedPlace(world.nowMs)
        runCurrent()

        scene.fix(northMetres = DRIVEN_METRES + 600, second = threeHoursOn + 20)
        scene.fix(northMetres = DRIVEN_METRES + 900, second = threeHoursOn + 50)

        val (first, second) = world.trips.rows
        assertEquals(TripStatus.OPEN, second.status)
        assertNotNull(first.endLatitude)
        assertTrue(scene.service.recording)
    }

    @Test
    fun `the service is told where the truck is parked, and the geofence goes with a trip`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()

            val fence = checkNotNull(scene.service.fence)
            val (first) = world.trips.rows
            assertEquals(first.endLatitude!!, fence.latitude, 1e-9)
            assertEquals(first.endLongitude!!, fence.longitude, 1e-9)

            world.nowMs = scene.timeOf(threeHoursOn)
            scene.controller.onLeftParkedPlace(world.nowMs)
            runCurrent()
            scene.fix(northMetres = DRIVEN_METRES + 600, second = threeHoursOn + 20)
            scene.fix(northMetres = DRIVEN_METRES + 900, second = threeHoursOn + 50)

            assertTrue(scene.service.recording)
            assertNull(scene.service.fence)
        }

    @Test
    fun `leaving a place with no wait to watch only remembers`() = runTest {
        val scene = ParkedScene(this, world)
        scene.connect()

        scene.controller.onLeftParkedPlace(world.nowMs)
        runCurrent()

        assertTrue(scene.service.recording)
        assertNull(scene.service.fastGpsUntilMs)
        assertNull(scene.service.fence)
    }
}
