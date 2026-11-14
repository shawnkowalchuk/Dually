package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.time.testLocale
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.DEFAULT_GRACE_PERIOD_SECONDS
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The iPhone's Settings (phase 4g) over a real settings file: each change lands in the file
 * Android's code reads, with Android's ranges and words.
 */
class IosSettingsTest {
    private val world = RecorderWorld()
    private val settings = IosSettings(world.settings) { testLocale("en-CA") }

    @AfterTest
    fun cleanUp() = world.close()

    @Test
    fun `out of the box it reads as Android's Settings does`() = runTest {
        val text = settings.read()
        assertEquals("2 min", text.grace)
        assertEquals("10 min", text.parkedLimit)
        assertEquals("0.3 km", text.shortestTrip)
        assertEquals("0 %", text.adjustment)
        assertEquals("5 days · 42.5 h a week", text.week)
        assertFalse(text.miles)
        assertTrue(text.drivingAlert)
    }

    @Test
    fun `a step is stored at once and stops at the end of its range`() = runTest {
        assertEquals("2.5 min", settings.stepGrace(longer = true).grace)
        assertEquals(DEFAULT_GRACE_PERIOD_SECONDS + 30, world.settings.current().gracePeriodSeconds)
        repeat(30) { settings.stepGrace(longer = true) }
        val most = settings.read()
        assertEquals("10 min", most.grace)
        assertFalse(most.graceLonger)
        assertTrue(most.graceShorter)
    }

    @Test
    fun `miles change the shortest trip's words and what the shared code reads`() = runTest {
        val text = settings.setMiles(miles = true)
        assertTrue(text.miles)
        assertEquals("0.19 mi", text.shortestTrip)
        assertEquals(DistanceUnit.MILES, world.settings.current().distanceUnit)
    }

    @Test
    fun `the adjustment goes in half percents with Android's signs`() = runTest {
        settings.stepAdjustment(up = true)
        assertEquals("+1 %", settings.stepAdjustment(up = true).adjustment)
        repeat(4) { settings.stepAdjustment(up = false) }
        assertEquals("−1 %", settings.read().adjustment)
    }

    @Test
    fun `hours are set on every day and refused when the end is not after the start`() = runTest {
        assertTrue(settings.setHours(startMinute = 7 * 60, endMinute = 15 * 60 + 30))
        val text = settings.read()
        assertEquals(7 * 60, text.startMinute)
        assertEquals("5 days · 42.5 h a week", text.week)
        assertFalse(settings.setHours(startMinute = 9 * 60, endMinute = 9 * 60))
        assertEquals(7 * 60, settings.read().startMinute)
    }

    @Test
    fun `a work day switched off leaves the week shorter`() = runTest {
        val text = settings.setWorkDay(dayIndex = 4, tracked = false)
        assertFalse(text.days[4].tracked)
        assertEquals("4 days · 34 h a week", text.week)
    }
}
