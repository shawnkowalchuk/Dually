package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.party.PopAtTripStart
import com.shawnkowalchuk.milo.core.trip.TRACK_START_WALL_CLOCK_MS
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.core.trip.fixAt
import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.settings.setSoundEnabled
import com.shawnkowalchuk.milo.data.settings.useBuiltInSound
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * The iPhone's connect and trip-start sounds (2026-11-16) at Android's moments: the connect sound
 * when a trip really begins, the trip-start sound once when it is first seen driving, at once for
 * a trip a parked truck's moving starts, and what each plays by the settings.
 */
class IosTripSoundsTest {
    private val world = RecorderWorld()

    @AfterTest
    fun cleanUp() = world.close()

    @Test
    fun `a connect plays the connect sound and the drive-off the trip-start one once`() = runTest {
        val recorder = world.recorder()
        assertNotNull(recorder.truckConnected())
        assertEquals(TripSounds(listOf("CONNECT"), tripStarts = true), recorder.takeSounds())
        assertNull(recorder.takeSounds(), "handed over once")

        world.drive(recorder, driveNorth(fixCount = 24, metresPerFix = 75.0))
        assertEquals(TripSounds(listOf("DRIVING_OFF"), tripStarts = false), recorder.takeSounds())

        val more =
            driveNorth(
                fixCount = 4,
                metresPerFix = 75.0,
                fromNorthMetres = 1_800.0,
                fromSecond = 200,
            )
        world.drive(recorder, more)
        assertNull(recorder.takeSounds(), "the trip-start sound plays once per trip")
    }

    @Test
    fun `a parked truck driving off has the trip-start sound as its first`() = runTest {
        val recorder = world.recorder()
        recorder.truckConnected()
        world.drive(recorder, driveNorth(fixCount = 24, metresPerFix = 75.0))
        world.drive(recorder, (1..12).map { minute -> fixAt(1_725.0, second = 115 + minute * 60) })
        recorder.takeSounds()

        val drivingOff =
            driveNorth(
                fixCount = 8,
                metresPerFix = 75.0,
                fromNorthMetres = 1_725.0,
                fromSecond = 900,
            )
        world.drive(recorder, drivingOff)
        assertEquals(TripSounds(listOf("DRIVING_OFF"), tripStarts = true), recorder.takeSounds())
    }

    @Test
    fun `each moment plays the sound chosen and a switch that is off plays nothing`() = runTest {
        val sounds = IosSounds(world.settings, world.log, { TRACK_START_WALL_CLOCK_MS })
        world.settings.useBuiltInSound(TripSound.CONNECT, BuiltInSound.COIN)
        world.settings.setSoundEnabled(TripSound.DRIVING_OFF, false)

        val played = sounds.toPlay(TripSounds(listOf("CONNECT", "DRIVING_OFF"), tripStarts = true))

        assertEquals(listOf("sound_coin"), played.sounds.map { it.file })
        assertEquals("Connect sound", played.sounds.single().label)
    }

    @Test
    fun `on the party's day the trip's first sound gives way to the pop`() = runTest {
        val sounds =
            IosSounds(world.settings, world.log, { TRACK_START_WALL_CLOCK_MS }) {
                PopAtTripStart.AT_ONCE
            }

        val played = sounds.toPlay(TripSounds(listOf("CONNECT"), tripStarts = true))

        assertEquals(listOf("party_pop"), played.sounds.map { it.file })
    }
}
