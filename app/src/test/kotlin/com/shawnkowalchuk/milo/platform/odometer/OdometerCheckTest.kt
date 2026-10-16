package com.shawnkowalchuk.milo.platform.odometer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a trip asks whether its vehicle's odometer is still right (2026-10-16). */
class OdometerCheckTest {
    private val ford = "1C:E1:92:A0:F8:8D"
    private val ram = "00:11:22:33:44:55"

    @Test
    fun `a trip in another vehicle than the last asks, with several vehicles paired`() {
        assertTrue(asksAboutOdometer(ram, lastVehicle = ford, pairedVehicles = 2))
    }

    @Test
    fun `the same vehicle again does not ask, whatever the case of its address`() {
        assertFalse(asksAboutOdometer(ford, lastVehicle = ford, pairedVehicles = 2))
        assertFalse(asksAboutOdometer(ford.lowercase(), lastVehicle = ford, pairedVehicles = 2))
    }

    @Test
    fun `one vehicle, or no trip before, never asks`() {
        assertFalse(asksAboutOdometer(ram, lastVehicle = ford, pairedVehicles = 1))
        assertFalse(asksAboutOdometer(ram, lastVehicle = null, pairedVehicles = 2))
    }
}
