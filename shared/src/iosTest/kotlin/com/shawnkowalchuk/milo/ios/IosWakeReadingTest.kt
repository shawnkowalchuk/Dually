package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.trip.TRACK_START_WALL_CLOCK_MS
import com.shawnkowalchuk.milo.core.trip.driveNorth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * A wake for a drive that finds the truck connected (2026-11-16, ADR-015's second layer): it is
 * Android's reading of the truck, so a trip starts, unless the rules hold automatic start off
 * after End was pressed with the truck connected.
 */
class IosWakeReadingTest {
    private val world = RecorderWorld()

    @AfterTest
    fun cleanUp() = world.close()

    @Test
    fun `a wake that finds the truck starts the trip the automation missed`() = runTest {
        val recorder = world.recorder()
        assertNull(recorder.now(), "no trip open")

        val started = assertNotNull(recorder.truckReadConnected())

        assertTrue(recorder.wantsGps)
        assertNotNull(recorder.takeSounds(), "it has its connect sound, as on Android")
        assertTrue(
            world.logLines().any {
                it.message.startsWith("A wake for a drive found the truck")
            },
        )
        assertNotNull(world.trips.findTrip(started.tripId))
    }

    @Test
    fun `End with the truck connected keeps a wake from starting a trip`() = runTest {
        val recorder = world.recorder()
        recorder.truckConnected()
        world.drive(recorder, driveNorth(fixCount = 12, metresPerFix = 75.0))
        world.nowMs = TRACK_START_WALL_CLOCK_MS + 200_000
        assertNull(recorder.end(), "ended by hand, the truck still connected: held off")

        world.nowMs = TRACK_START_WALL_CLOCK_MS + 260_000
        assertNull(recorder.truckReadConnected(), "a reading is no new link: the hold-off stands")
    }
}
