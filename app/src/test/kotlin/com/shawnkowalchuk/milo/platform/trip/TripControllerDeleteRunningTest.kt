package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.ON_HOME
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import com.shawnkowalchuk.milo.data.trip.TripCorrections
import com.shawnkowalchuk.milo.data.trip.TripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bin on the trip being recorded (2026-11-03): Home ends the trip as End does and, once the
 * controller has caught up, deletes it as Trips does (`HomeViewModel.onDeleteRunning`). This
 * is that sequence against the trip controller and the stand-in trips table.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerDeleteRunningTest {
    private val world = FakeWorld()

    @Test
    fun `the trip is closed before it is deleted, and automatic start stays held off`() = runTest {
        val scene = ParkedScene(this, world)
        scene.connect()
        drive(world, scene.controller, fixCount = 21, metresPerFix = 100.0)
        val tripId = world.trips.rows.single().id
        val corrections =
            TripCorrections(TripRepository(world.trips), EventLogRepository(world.log)) {
                world.nowMs
            }

        scene.controller.onTrigger(TripTrigger.MANUAL_END, "Delete button")
        scene.controller.whenCaughtUp {
            launch { corrections.apply(tripId, TripCorrection.DELETE, ON_HOME) }
        }
        runCurrent()

        assertEquals(TripStatus.DELETED, world.trips.rows.single().status)
        assertTrue(
            world.logged(EventCategory.TRIP).last().startsWith(
                "Trip $tripId: deleted with the bin on Home, while it was recorded",
            ),
        )
        // The truck is still connected: no new trip until it disconnects (his choice).
        assertEquals(world.nowMs, world.settings.current().autoStartHeldOffSinceMs)
        assertEquals(1, world.trips.rows.size)
    }
}
