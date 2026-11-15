package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.systemZoneId
import com.shawnkowalchuk.milo.core.trip.TRACK_START_WALL_CLOCK_MS
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.data.settings.addOdometerReading
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Home's odometer on the iPhone (2026-11-14): the figure Settings shows, standing, and while a
 * trip with the truck in it is driven, counting. The rule is the shared one Android's Home uses
 * (`homeOdometers`, tested in `HomeOdometerTest`); this checks the iPhone hands it the trip.
 */
class IosHomeOdometerTest {
    private val world = RecorderWorld()
    private val odometer =
        IosHomeOdometer(world.settings, world.trips, ::systemZoneId) { Locale("en_CA") }

    @AfterTest
    fun cleanUp() = world.close()

    @Test
    fun `before the first reading the tile asks for one`() = runTest {
        val shown = odometer.read(trip = null, nowMs = world.nowMs)
        assertFalse(shown.hasReading)
        assertEquals(
            "Type the number on the dashboard in Settings once. Dually adds every trip to it " +
                "from then on.",
            shown.caption,
        )
    }

    @Test
    fun `a reading stands on the wheels and says where it comes from`() = runTest {
        typeReading()
        val shown = odometer.read(trip = null, nowMs = world.nowMs)
        assertTrue(shown.hasReading)
        assertEquals(123_456, shown.whole)
        assertFalse(shown.counting)
        assertEquals("123,456 kilometres", shown.spoken)
        assertEquals("Your last reading in Settings, plus every trip since", shown.caption)
    }

    @Test
    fun `a trip the automation started turns the last wheel as it is driven`() = runTest {
        typeReading()
        val recorder = world.recorder()
        assertNotNull(recorder.truckConnected())
        val trip =
            assertNotNull(world.drive(recorder, driveNorth(fixCount = 24, metresPerFix = 75.0)))
        assertTrue(trip.truckSeen)

        val shown = odometer.read(trip, nowMs = world.nowMs)
        assertTrue(shown.counting)
        assertEquals(123_457, shown.whole, "1.7 km on from 123,456")
        assertTrue(shown.turn > 0.0 && shown.turn < 1.0)
        val soFar = formatDistance(trip.metres, DistanceUnit.KILOMETRES, Locale("en_CA"))
        assertEquals("+$soFar km this trip", shown.caption)
    }

    @Test
    fun `a trip by the button that the truck has not joined moves no odometer`() = runTest {
        typeReading()
        world.truckBelief(connected = false)
        val recorder = world.recorder()
        assertNotNull(recorder.start())
        val trip =
            assertNotNull(world.drive(recorder, driveNorth(fixCount = 24, metresPerFix = 75.0)))
        assertFalse(trip.truckSeen)

        val shown = odometer.read(trip, nowMs = world.nowMs)
        assertFalse(shown.counting)
        assertEquals(123_456, shown.whole)
        assertEquals("Your last reading in Settings, plus every trip since", shown.caption)
    }

    private suspend fun typeReading() {
        val readAt = TRACK_START_WALL_CLOCK_MS - HOUR_MS
        world.settings.addOdometerReading(OdometerReading(readAt, 123_456, DistanceUnit.KILOMETRES))
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
