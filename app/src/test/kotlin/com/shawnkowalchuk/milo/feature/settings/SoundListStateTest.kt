package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.OwnSound
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.settings.builtInSounds
import com.shawnkowalchuk.milo.platform.trip.OwnSoundRefusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val R2D2 = "file:/data/trip_sound/own_trip_start_sound_1"
private const val RACE = "file:/data/trip_sound/own_trip_start_sound_2"

/** What the screen of one of the two sounds shows for the settings as they are stored. */
class SoundListStateTest {
    private val twoOfHisOwn =
        listOf(OwnSound(R2D2, "r2d2.mp3"), OwnSound(RACE, name = null))

    /** The rows that are marked as in use, of both lists. */
    private fun SoundListUiState.Ready.marked(): List<Any> =
        builtIn.filter { it.inUse }.map { it.sound } + own.filter { it.inUse }.map { it.uri }

    @Test
    fun `out of the box each screen marks the first sound of its list`() {
        val connect = soundListState(MiloSettings(), TripSound.CONNECT)
        val tripStart = soundListState(MiloSettings(), TripSound.DRIVING_OFF)

        assertEquals(listOf<Any>(BuiltInSound.CHIRP), connect.marked())
        assertEquals(BuiltInSound.CHIRP, connect.builtIn.first().sound)
        assertEquals(listOf<Any>(BuiltInSound.CHIME), tripStart.marked())
        assertEquals(BuiltInSound.CHIME, tripStart.builtIn.first().sound)
        assertTrue(connect.own.isEmpty())
        assertFalse(connect.copying)
        assertNull(connect.problem)
    }

    @Test
    fun `each screen lists the twelve in its own order`() {
        for (which in TripSound.entries) {
            val state = soundListState(MiloSettings(), which)

            assertEquals(which, state.which)
            assertEquals(which.builtInSounds(), state.builtIn.map { it.sound })
        }
    }

    @Test
    fun `a chosen built-in sound is the one row that is marked, on its own screen only`() {
        for (sound in BuiltInSound.entries) {
            val settings = MiloSettings(drivingOffBuiltIn = sound)

            assertEquals(
                listOf<Any>(sound),
                soundListState(settings, TripSound.DRIVING_OFF).marked(),
            )
            assertEquals(
                listOf<Any>(BuiltInSound.CHIRP),
                soundListState(settings, TripSound.CONNECT).marked(),
            )
        }
    }

    @Test
    fun `his own sounds are on both screens, and the one in use is marked where it is in use`() {
        val settings =
            MiloSettings(
                customSoundUri = RACE,
                connectBuiltIn = BuiltInSound.COIN,
                drivingOffBuiltIn = BuiltInSound.HORN_TAP,
                ownSounds = twoOfHisOwn,
            )

        val connect = soundListState(settings, TripSound.CONNECT)
        val tripStart = soundListState(settings, TripSound.DRIVING_OFF)

        assertEquals(
            listOf(OwnRow(R2D2, "r2d2.mp3", inUse = false), OwnRow(RACE, null, inUse = true)),
            connect.own,
        )
        // His own is the one in use there: no built-in row is marked beside it.
        assertEquals(listOf<Any>(RACE), connect.marked())
        assertEquals(twoOfHisOwn.map { it.uri }, tripStart.own.map { it.uri })
        assertEquals(listOf<Any>(BuiltInSound.HORN_TAP), tripStart.marked())
    }

    @Test
    fun `a stored sound this build does not know leaves the first of the list marked`() {
        // The settings store reads such a name as "none chosen".
        val state = soundListState(MiloSettings(connectBuiltIn = null), TripSound.CONNECT)

        assertEquals(listOf<Any>(BuiltInSound.CHIRP), state.marked())
    }

    @Test
    fun `exactly one row is marked, however the settings stand`() {
        val every =
            buildList {
                for (ownUri in listOf(null, R2D2)) {
                    for (builtIn in listOf(null) + BuiltInSound.entries) {
                        for (enabled in listOf(true, false)) {
                            add(
                                MiloSettings(
                                    soundEnabled = enabled,
                                    customSoundUri = ownUri,
                                    connectBuiltIn = builtIn,
                                    ownSounds = twoOfHisOwn,
                                ),
                            )
                        }
                    }
                }
            }

        for (settings in every) {
            assertEquals("$settings", 1, soundListState(settings, TripSound.CONNECT).marked().size)
        }
    }

    @Test
    fun `the switch does not change what is listed or marked`() {
        val on = MiloSettings(connectBuiltIn = BuiltInSound.KEY_FOB, ownSounds = twoOfHisOwn)

        assertEquals(
            soundListState(on, TripSound.CONNECT),
            soundListState(on.copy(soundEnabled = false), TripSound.CONNECT),
        )
    }

    @Test
    fun `a copy under way and a problem are passed through as they are`() {
        val state =
            soundListState(
                MiloSettings(),
                TripSound.DRIVING_OFF,
                copying = true,
                problem = SoundListProblem.TOO_LARGE,
            )

        assertTrue(state.copying)
        assertEquals(SoundListProblem.TOO_LARGE, state.problem)
    }

    @Test
    fun `each reason a picked file is refused has words of its own`() {
        assertEquals(SoundListProblem.COULD_NOT_COPY, OwnSoundRefusal.COULD_NOT_COPY.asProblem())
        assertEquals(SoundListProblem.TOO_LARGE, OwnSoundRefusal.TOO_LARGE.asProblem())
        assertEquals(SoundListProblem.NOT_PLAYABLE, OwnSoundRefusal.NOT_PLAYABLE.asProblem())
    }
}
