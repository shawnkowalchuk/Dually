package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.time.testZone
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.platform.driving.DrivingAlertStep
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The driving alert on the iPhone (phase 4d, third part): Core Motion's history turned into
 * Android's reports, and Android's rules deciding. The rules' every reason is tested in the
 * app (DrivingAlertRulesTest); this checks what the iPhone hands them.
 */
class IosDrivingAlertTest {
    private val world = RecorderWorld().also { it.nowMs = FRIDAY_1PM }
    private val memory =
        object : MotionMemory {
            override var reportedInVehicle = false
            override var lastLookAtMs: Long? = null
        }
    private val recorder = world.recorder()
    private val alert =
        IosDrivingAlert(
            recorder = recorder,
            trips = world.trips,
            settings = world.settings,
            eventLog = world.log,
            memory = memory,
            zone = { testZone("America/Edmonton") },
            clock = { world.nowMs },
        )

    @AfterTest
    fun cleanUp() = world.close()

    @Test
    fun `getting into a vehicle in the work hours with no trip shows the alert once`() = runTest {
        assertEquals(DrivingAlertStep.SHOW, alert.motion(drive(), true, true))
        assertNotNull(world.settings.current().lastDrivingAlertAtMs)
        val line = world.logLines().last { it.category == EventCategory.DRIVING }
        assertTrue(line.message.contains("Alert shown"), line.message)

        // The same drive at the next wake: no change, so nothing is judged and nothing logged.
        world.nowMs += 5 * MINUTE_MS
        val linesBefore = world.logLines().size
        assertEquals(DrivingAlertStep.LEAVE, alert.motion(drive(), true, true))
        assertEquals(linesBefore, world.logLines().size)
    }

    @Test
    fun `leaving the vehicle takes the alert away`() = runTest {
        alert.motion(drive(), true, true)
        world.nowMs += 20 * MINUTE_MS
        val walked = listOf(MotionRecord(world.nowMs - MINUTE_MS, inVehicle = false))
        assertEquals(DrivingAlertStep.WITHDRAW, alert.motion(walked, true, true))
    }

    @Test
    fun `a second drive within half an hour does not alert again`() = runTest {
        alert.motion(drive(), true, true)
        world.nowMs += 10 * MINUTE_MS
        val stopAndGo =
            listOf(
                MotionRecord(world.nowMs - 4 * MINUTE_MS, inVehicle = false),
                MotionRecord(world.nowMs - MINUTE_MS, inVehicle = true),
            )
        assertEquals(DrivingAlertStep.LEAVE, alert.motion(stopAndGo, true, true))
    }

    @Test
    fun `a trip being recorded takes the alert away`() = runTest {
        assertNotNull(recorder.start())
        assertEquals(DrivingAlertStep.WITHDRAW, alert.motion(drive(), true, true))
    }

    @Test
    fun `a tap on the alert starts a trip by hand and says where it came from`() = runTest {
        alert.motion(drive(), true, true)
        val started = assertNotNull(recorder.startFromAlert())
        assertEquals(TripStartCause.MANUAL, world.trips.findTrip(started.tripId)?.startedBy)
        assertTrue(world.logLines().any { it.message == "tap on the driving alert" })
        // The trip being recorded now takes the alert away at the next wake.
        world.nowMs += 2 * MINUTE_MS
        val stopped = listOf(MotionRecord(world.nowMs - MINUTE_MS, inVehicle = false))
        assertEquals(DrivingAlertStep.WITHDRAW, alert.motion(stopped, true, true))
    }

    @Test
    fun `no vehicle chosen in Setup means no alert`() = runTest {
        assertEquals(DrivingAlertStep.WITHDRAW, alert.motion(drive(), false, true))
    }

    @Test
    fun `outside the work hours there is no alert`() = runTest {
        world.nowMs = SATURDAY_1PM
        assertEquals(DrivingAlertStep.WITHDRAW, alert.motion(drive(), true, true))
    }

    @Test
    fun `a truck the automation said is connected with nothing watching it still alerts`() =
        runTest {
            // A missed disconnect leaves "connected" behind, and no trip starts by itself.
            world.truckBelief(connected = true)
            assertEquals(DrivingAlertStep.SHOW, alert.motion(drive(), true, true))
        }

    @Test
    fun `history Core Motion is unsure of is passed over`() = runTest {
        val unsure = listOf(MotionRecord(world.nowMs - MINUTE_MS, inVehicle = null))
        assertEquals(DrivingAlertStep.LEAVE, alert.motion(unsure, true, true))
    }

    /** Core Motion's history: in a vehicle since two minutes ago. */
    private fun drive() = listOf(MotionRecord(world.nowMs - 2 * MINUTE_MS, inVehicle = true))

    private companion object {
        const val MINUTE_MS = 60_000L

        /** 2026-11-13, a Friday, 13:00 in Edmonton (19:00 UTC): inside the default work hours. */
        const val FRIDAY_1PM = 1_794_596_400_000L

        /** 2026-11-14, a Saturday, 13:00 in Edmonton: no work hours by default. */
        const val SATURDAY_1PM = FRIDAY_1PM + 24 * 60 * MINUTE_MS
    }
}
