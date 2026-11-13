package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.time.testLocale
import com.shawnkowalchuk.milo.core.time.testZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which row of the iPhone's Setup screen says what (phase 4d, second part). */
class IosSetupTest {
    private fun screen(
        location: LocationAccess = LocationAccess.ALWAYS,
        precise: Boolean = true,
        alwaysAsked: Boolean = true,
        vehicle: VehicleLink? = VehicleLink.BLUETOOTH,
        connectHeardAtMs: Long? = NOW - HOUR_MS,
        disconnectHeardAtMs: Long? = NOW - DAY_MS,
        startMade: Boolean = false,
        endMade: Boolean = false,
        motion: Permission = Permission.ALLOWED,
        notifications: Permission = Permission.ALLOWED,
    ) = setupScreen(
        location = location,
        precise = precise,
        alwaysAsked = alwaysAsked,
        vehicle = vehicle,
        connectHeardAtMs = connectHeardAtMs,
        disconnectHeardAtMs = disconnectHeardAtMs,
        startMade = startMade,
        endMade = endMade,
        motion = motion,
        notifications = notifications,
        nowMs = NOW,
        zone = testZone("America/Edmonton"),
        locale = testLocale("en-CA"),
    )

    @Test
    fun `a first start has every row to do and the automations waiting for the vehicle`() {
        val first =
            screen(
                location = LocationAccess.NOT_ASKED,
                precise = false,
                alwaysAsked = false,
                vehicle = null,
                connectHeardAtMs = null,
                disconnectHeardAtMs = null,
                motion = Permission.NOT_ASKED,
                notifications = Permission.NOT_ASKED,
            )
        assertEquals(0, first.ready)
        assertEquals(7, first.total)
        assertEquals(SetupAction.ASK_LOCATION, first.rows[0].action)
        assertEquals(SetupState.WAITING, first.rows[1].state)
        assertEquals(SetupAction.CHOOSE_VEHICLE, first.rows[2].action)
        assertEquals(SetupState.WAITING, first.rows[3].state)
        assertEquals(SetupState.WAITING, first.rows[4].state)
    }

    @Test
    fun `While Using is asked up to Always once and then sent to Settings`() {
        assertEquals(
            SetupAction.ASK_ALWAYS,
            screen(location = LocationAccess.WHILE_USING, alwaysAsked = false).rows[0].action,
        )
        assertEquals(
            SetupAction.OPEN_SETTINGS,
            screen(location = LocationAccess.WHILE_USING, alwaysAsked = true).rows[0].action,
        )
    }

    @Test
    fun `approximate location needs fixing in Settings`() {
        val row = screen(precise = false).rows[1]
        assertEquals(SetupState.NEEDS_YOU, row.state)
        assertEquals(SetupAction.OPEN_SETTINGS, row.action)
    }

    @Test
    fun `an automation is ready once it has run and says when`() {
        val done = screen()
        assertTrue(done.complete)
        assertTrue(done.rows[3].sentence.startsWith("Working: it last ran today at 12:00"))
        assertTrue(done.rows[4].sentence.startsWith("Working: it last ran Thu, Nov 12 at 1:00"))

        val notYet = screen(disconnectHeardAtMs = null)
        assertFalse(notYet.complete)
        assertEquals(6, notYet.ready)
        assertEquals(SetupAction.OPEN_SHORTCUTS, notYet.rows[4].action)
    }

    @Test
    fun `the steps name the vehicle's own trigger and Dually's action`() {
        val bluetooth = screen(
            vehicle = VehicleLink.BLUETOOTH,
            connectHeardAtMs = null,
        ).rows[3].steps
        assertTrue(bluetooth.any { "Is Connected" in it })
        assertTrue(bluetooth.last().contains("Start a trip"))

        val carPlayEnd = screen(
            vehicle = VehicleLink.CARPLAY,
            disconnectHeardAtMs = null,
        ).rows[4].steps
        assertTrue(carPlayEnd.any { "Disconnects only" in it })
        assertTrue(carPlayEnd.last().contains("End a trip"))
    }

    @Test
    fun `I've made it makes an automation row ready on the user's word until it runs`() {
        val notMade = screen(connectHeardAtMs = null, disconnectHeardAtMs = null)
        assertEquals(SetupAction.CONFIRM_START_MADE, notMade.rows[3].confirmAction)
        assertEquals(SetupAction.CONFIRM_END_MADE, notMade.rows[4].confirmAction)
        assertFalse(notMade.complete)

        val made =
            screen(
                connectHeardAtMs = null,
                disconnectHeardAtMs = null,
                startMade = true,
                endMade = true,
            )
        assertTrue(made.complete)
        assertTrue(made.rows[3].sentence.startsWith("Made, by your word."))
        assertEquals(null, made.rows[3].confirmButton)

        // Once it has run, that is what the row says.
        val ran = screen(disconnectHeardAtMs = null, startMade = true)
        assertTrue(ran.rows[3].sentence.startsWith("Working: it last ran"))
    }

    @Test
    fun `the driving alert's rows are asked for but do not hold trips back`() {
        val noAlert = screen(motion = Permission.NOT_ASKED, notifications = Permission.OFF)
        assertTrue(noAlert.complete)
        assertEquals(5, noAlert.ready)
        assertEquals(SetupAction.ASK_MOTION, noAlert.rows[5].action)
        assertEquals(SetupAction.OPEN_SETTINGS, noAlert.rows[6].action)
        assertFalse(noAlert.rows[5].required)
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS

        /** 2026-11-13 19:00 UTC: 13:00 in Edmonton (UTC-6 since 2026-11-01), a Friday. */
        const val NOW = 1_794_596_400_000L
    }
}
