package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TRACK_START_WALL_CLOCK_MS
import com.shawnkowalchuk.milo.core.trip.fixAt
import com.shawnkowalchuk.milo.data.settings.TripSound
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 6 m a second, 21.6 km/h: a truck pulling out. */
private const val PULLING_OUT = 6f

/**
 * Which of a trip's sounds the trip controller says is its first (`TripRecorder.record`,
 * `tripStarts`): the one the pop of MilO's one-year party takes the place of on its day. Once
 * per trip: the connect sound where the trip has one, and the trip-start sound of a trip that a
 * parked truck's moving started, which has none.
 *
 * The sounds themselves are as they were: `TripControllerDrivingOffTest` and
 * `TripControllerWaitingTest` hold them, and were not changed for this.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerFirstSoundTest {
    private val world = FakeWorld()

    private fun TestScope.fix(
        controller: TripController,
        north: Double,
        second: Int,
        speed: Float,
    ) {
        world.nowMs = TRACK_START_WALL_CLOCK_MS + second * 1000L
        val fix = fixAt(northMetres = north, second = second, speedMetresPerSecond = speed)
        controller.onFix(fix.asFix())
        runCurrent()
    }

    private fun TestScope.connected(): Pair<TripController, FakeService> {
        world.truck.connected = true
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        runCurrent()
        return controller to service
    }

    @Test
    fun `a trip the truck starts has the connect sound for its first, and the chime is not one`() =
        runTest {
            val (controller, service) = connected()
            assertEquals(listOf(listOf(TripSound.CONNECT)), service.firstSoundsOfTrips)

            fix(controller, north = 0.0, second = 0, speed = 0f)
            fix(controller, north = 30.0, second = 5, speed = PULLING_OUT)
            fix(controller, north = 100.0, second = 10, speed = 15f)

            assertEquals(listOf(TripSound.CONNECT, TripSound.DRIVING_OFF), service.sounds)
            assertEquals(listOf(listOf(TripSound.CONNECT)), service.firstSoundsOfTrips)
        }

    @Test
    fun `a trip started with Start has the connect sound for its first`() = runTest {
        val (controller, service) = recordingManualTrip(world)
        fix(controller, north = 0.0, second = 0, speed = 0f)
        fix(controller, north = 30.0, second = 5, speed = PULLING_OUT)

        assertEquals(listOf(TripSound.CONNECT, TripSound.DRIVING_OFF), service.sounds)
        assertEquals(listOf(listOf(TripSound.CONNECT)), service.firstSoundsOfTrips)
    }

    @Test
    fun `a trip a parked truck's moving started has its chime for its first`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        // The first trip: its connect sound, and no other.
        assertEquals(listOf(listOf(TripSound.CONNECT)), scene.service.firstSoundsOfTrips)

        scene.standThenDriveOff()

        assertEquals(2, world.trips.rows.size)
        assertEquals(
            listOf(listOf(TripSound.CONNECT), listOf(TripSound.DRIVING_OFF)),
            scene.service.firstSoundsOfTrips,
        )
        // And the sounds are what they were before anything was said about a first.
        assertEquals(
            listOf(TripSound.CONNECT, TripSound.DRIVING_OFF, TripSound.DRIVING_OFF),
            scene.service.sounds,
        )
    }

    @Test
    fun `a trip picked up after a restart has no first sound a second time`() = runTest {
        val (before, first) = connected()
        fix(before, north = 0.0, second = 0, speed = 0f)
        assertEquals(1, first.firstSoundsOfTrips.size)

        val (controller, service) = world.newProcess(backgroundScope)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.RECONCILE, "process start")
        runCurrent()
        // It drives off after the restart: the chime, which is not its first sound.
        fix(controller, north = 30.0, second = 5, speed = PULLING_OUT)

        assertEquals(listOf(TripSound.DRIVING_OFF), service.sounds)
        assertEquals(emptyList<List<TripSound>>(), service.firstSoundsOfTrips)
    }

    @Test
    fun `a trip the companion callback opened has its first sound when the truck is confirmed`() =
        runTest {
            val (controller, service) = process(world)
            service.comesUpAtOnce = true
            controller.onTrigger(TripTrigger.TRUCK_APPEARED, "companion service")
            runCurrent()
            fix(controller, north = 0.0, second = 0, speed = 0f)
            fix(controller, north = 30.0, second = 5, speed = PULLING_OUT)
            assertEquals(emptyList<List<TripSound>>(), service.firstSoundsOfTrips)

            world.truck.connected = true
            controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "Bluetooth receiver")
            runCurrent()

            // Both sounds in one order, and that order is the trip's first.
            assertEquals(
                listOf(listOf(TripSound.CONNECT, TripSound.DRIVING_OFF)),
                service.firstSoundsOfTrips,
            )
        }

    @Test
    fun `a false start has no first sound, as it has no sound at all`() = runTest {
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.TRUCK_APPEARED, "companion service")
        runCurrent()

        // Nothing confirms the truck: the trip is discarded when its time is up.
        world.nowMs += 60_000L
        controller.onCheckDue(checkNotNull(service.checkAtMs))
        runCurrent()

        assertEquals(emptyList<TripSound>(), service.sounds)
        assertEquals(emptyList<List<TripSound>>(), service.firstSoundsOfTrips)
    }

    @Test
    fun `every trip of a day has one first sound`() = runTest {
        val (controller, service) = recordingManualTrip(world)
        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()
        world.nowMs += 600_000L
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()
        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()
        world.nowMs += 600_000L
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()

        assertEquals(List(3) { listOf(TripSound.CONNECT) }, service.firstSoundsOfTrips)
    }
}
