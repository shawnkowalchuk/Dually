package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.time.systemZoneId
import com.shawnkowalchuk.milo.core.trip.TRACK_START_WALL_CLOCK_MS
import com.shawnkowalchuk.milo.core.trip.TrackPoint
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.core.trip.fixAt
import com.shawnkowalchuk.milo.data.buildMiloDatabase
import com.shawnkowalchuk.milo.data.buildPointsDatabase
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.settings.buildSettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.TripLedger
import com.shawnkowalchuk.milo.platform.trip.TripRuleSettings
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem

/**
 * The iPhone's trip recorder with the truck as the Shortcuts automation reports it (phase 4d,
 * ADR-015), over real databases in a folder of their own. The rules themselves are tested in
 * commonTest; this checks that the iPhone hands them the events Android's worker does.
 */
class IosTripRecorderTest {
    private val folder =
        (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "recorder-${Random.nextLong()}").also {
            FileSystem.SYSTEM.createDirectories(it)
        }
    private val world = World(folder.toString())

    @AfterTest
    fun cleanUp() {
        world.close()
        FileSystem.SYSTEM.deleteRecursively(folder)
    }

    @Test
    fun `the automation's connect starts a trip and its disconnect ends it after the grace`() =
        runTest {
            val recorder = world.recorder()
            val started = assertNotNull(recorder.truckConnected())
            assertTrue(recorder.wantsGps)
            world.drive(recorder, driveNorth(fixCount = 24, metresPerFix = 75.0))

            world.nowMs = at(second = 120)
            assertNotNull(recorder.truckDisconnected(), "the grace period keeps the trip open")

            val afterGrace = 120 + GRACE_SECONDS + 1
            world.nowMs = at(second = afterGrace)
            assertNull(world.drive(recorder, listOf(fixAt(1_725.0, second = afterGrace))))
            assertFalse(recorder.wantsGps)
            val trip = assertNotNull(world.trips.findTrip(started.tripId))
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(TripStartCause.TRUCK, trip.startedBy)
        }

    @Test
    fun `the truck connecting again within the grace period keeps the trip`() = runTest {
        val recorder = world.recorder()
        val started = assertNotNull(recorder.truckConnected())
        world.drive(recorder, driveNorth(fixCount = 12, metresPerFix = 75.0))
        world.nowMs = at(second = 60)
        recorder.truckDisconnected()
        world.nowMs = at(second = 90)
        assertEquals(started.tripId, recorder.truckConnected()?.tripId)
        world.nowMs = at(second = 60 + GRACE_SECONDS + 30)
        assertEquals(
            started.tripId,
            world.drive(recorder, listOf(fixAt(900.0, second = 60 + GRACE_SECONDS + 30)))?.tripId,
        )
    }

    @Test
    fun `End with the truck connected holds automatic start off until a later link`() = runTest {
        val recorder = world.recorder()
        assertNotNull(recorder.truckConnected())
        world.drive(recorder, driveNorth(fixCount = 24, metresPerFix = 75.0))
        world.nowMs = at(second = 120)
        assertNull(recorder.end())

        world.nowMs = at(second = 130)
        assertNull(recorder.truckConnected(), "the same link is still up: no new trip")

        world.nowMs = at(second = 200)
        assertNotNull(recorder.truckConnected(), "a link a minute later is a new one")
    }

    @Test
    fun `a trip open when the app stopped carries on while the truck is believed connected`() =
        runTest {
            val first = world.recorder()
            val started = assertNotNull(first.truckConnected())
            world.drive(first, driveNorth(fixCount = 12, metresPerFix = 75.0))

            val afterRestart = world.recorder()
            world.nowMs = at(second = 70)
            assertEquals(started.tripId, afterRestart.now()?.tripId)
            assertTrue(afterRestart.wantsGps)
        }

    @Test
    fun `a belief left at connected starts no trip when the app is opened`() = runTest {
        world.truckBelief(connected = true)
        val opened = world.recorder()
        assertNull(opened.now(), "a disconnect the automation missed must not start trips")
        assertFalse(opened.wantsGps)
        assertNotNull(opened.truckConnected(), "the automation's next connect starts one")
    }

    @Test
    fun `a truck parked while connected is waited beside and its drive-off starts a trip`() =
        runTest {
            val recorder = world.recorder()
            val first = assertNotNull(recorder.truckConnected())
            world.drive(recorder, driveNorth(fixCount = 24, metresPerFix = 75.0))

            // Parked at 1725 m north, still connected, past the 10-minute parked limit.
            val parked = (1..12).map { minute -> fixAt(1_725.0, second = 115 + minute * 60) }
            assertNull(world.drive(recorder, parked))
            assertTrue(recorder.wantsGps, "the GPS watches the parked truck")
            assertEquals(TripStatus.FINISHED, world.trips.findTrip(first.tripId)?.status)

            val drivingOff =
                driveNorth(
                    fixCount = 8,
                    metresPerFix = 75.0,
                    fromNorthMetres = 1_725.0,
                    fromSecond = 900,
                )
            val next = assertNotNull(world.drive(recorder, drivingOff))
            assertNotEquals(first.tripId, next.tripId)
        }

    private class World(folder: String) {
        private val database = buildMiloDatabase(folder)
        private val points = buildPointsDatabase(folder)
        private val settings = buildSettingsStore(folder)
        private val log = EventLogRepository(database.eventLogDao())
        val trips = TripRepository(database.tripDao())
        var nowMs = TRACK_START_WALL_CLOCK_MS
        private val truck =
            object : TruckBelief {
                override var connected = false
            }

        fun truckBelief(connected: Boolean) {
            truck.connected = connected
        }

        fun recorder(): IosTripRecorder = IosTripRecorder(
            TripLedger(
                trips,
                RawPointRepository(points.rawPointDao()),
                settings,
                zone = ::systemZoneId,
            ),
            log,
            TripRuleSettings(settings, log) { nowMs },
            truck,
        ) { nowMs }

        suspend fun drive(recorder: IosTripRecorder, track: List<TrackPoint>): RecordingNow? {
            var last: RecordingNow? = null
            for (fix in track) {
                nowMs = fix.wallClockMs
                last =
                    recorder.addFix(
                        fix.wallClockMs,
                        fix.elapsedRealtimeMs,
                        fix.latitude,
                        fix.longitude,
                        fix.accuracyMetres,
                        fix.speedMetresPerSecond,
                    )
            }
            return last
        }

        fun close() {
            database.close()
            points.close()
        }
    }

    private companion object {
        const val GRACE_SECONDS = 120

        fun at(second: Int): Long = TRACK_START_WALL_CLOCK_MS + second * 1000L
    }
}
