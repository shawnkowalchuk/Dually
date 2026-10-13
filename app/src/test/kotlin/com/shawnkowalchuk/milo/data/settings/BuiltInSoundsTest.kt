package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The twelve sounds built into MilO (since 2026-10-13), and which of them each of the two
 * sounds plays for every way the settings file can stand: nothing stored, each built-in sound,
 * a name this build does not know, a sound of Shawn's own, and that sound gone again.
 */
class BuiltInSoundsTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)

    private val own = "file:/data/trip_sound/own_trip_start_sound_1"

    private suspend fun plays(which: TripSound): SoundChoice = store.current().sound(which)

    // ---- The lists ------------------------------------------------------------------------------

    @Test
    fun `there are twelve, and the name each is stored under is pinned`() {
        // A name is written to the settings file. Changed, a phone's choice would be read as
        // unknown and its sound would silently go back to the first of the list.
        assertEquals(
            listOf(
                "chirp", "seatbelt_chime", "key_fob", "turn_signal", "counter_bell", "coin",
                "sonar_ping", "chime", "horn_tap", "engine_rev", "burnout", "race_start",
            ),
            BuiltInSound.entries.map { it.storedName },
        )
        assertEquals(12, BuiltInSound.entries.map { it.storedName }.toSet().size)
    }

    @Test
    fun `the connect sound's list has the short ones first, then the others`() {
        assertEquals(
            listOf(
                BuiltInSound.CHIRP,
                BuiltInSound.SEATBELT_CHIME,
                BuiltInSound.KEY_FOB,
                BuiltInSound.TURN_SIGNAL,
                BuiltInSound.COUNTER_BELL,
                BuiltInSound.COIN,
                BuiltInSound.SONAR_PING,
                // The others.
                BuiltInSound.CHIME,
                BuiltInSound.HORN_TAP,
                BuiltInSound.ENGINE_REV,
                BuiltInSound.BURNOUT,
                BuiltInSound.RACE_START,
            ),
            TripSound.CONNECT.builtInSounds(),
        )
    }

    @Test
    fun `the trip-start sound's list has the livelier ones first, then the others`() {
        assertEquals(
            listOf(
                BuiltInSound.CHIME,
                BuiltInSound.HORN_TAP,
                BuiltInSound.ENGINE_REV,
                BuiltInSound.BURNOUT,
                BuiltInSound.RACE_START,
                // The others.
                BuiltInSound.CHIRP,
                BuiltInSound.SEATBELT_CHIME,
                BuiltInSound.KEY_FOB,
                BuiltInSound.TURN_SIGNAL,
                BuiltInSound.COUNTER_BELL,
                BuiltInSound.COIN,
                BuiltInSound.SONAR_PING,
            ),
            TripSound.DRIVING_OFF.builtInSounds(),
        )
    }

    @Test
    fun `both lists hold every sound once, and each begins with the sound MilO always played`() {
        for (which in TripSound.entries) {
            assertEquals(BuiltInSound.entries.toSet(), which.builtInSounds().toSet())
            assertEquals(12, which.builtInSounds().size)
        }
        assertEquals(BuiltInSound.CHIRP, TripSound.CONNECT.firstBuiltIn)
        assertEquals(BuiltInSound.CHIME, TripSound.DRIVING_OFF.firstBuiltIn)
    }

    // ---- What a sound plays, for every way the file can stand -------------------------------------

    @Test
    fun `nothing stored plays the chirp at the connect and the chime at the drive-off`() = runTest {
        assertEquals(SoundChoice(true, null, null, BuiltInSound.CHIRP), plays(TripSound.CONNECT))
        assertEquals(
            SoundChoice(true, null, null, BuiltInSound.CHIME),
            plays(TripSound.DRIVING_OFF),
        )
        // And the settings themselves say that nothing was chosen.
        assertEquals(MiloSettings(), store.current())
    }

    @Test
    fun `each of the twelve can be chosen for either sound, and the other sound is left alone`() =
        runTest {
            for (which in TripSound.entries) {
                val other = TripSound.entries.single { it != which }
                val otherBefore = plays(other)
                for (sound in BuiltInSound.entries) {
                    store.useBuiltInSound(which, sound)

                    assertEquals(SoundChoice(true, null, null, sound), plays(which))
                    assertEquals(otherBefore, plays(other))
                }
            }
        }

    @Test
    fun `a choice is stored under the sound's own key, by the sound's stored name`() = runTest {
        // A renamed key silently resets the choice on the phone, so the names are pinned here.
        store.useBuiltInSound(TripSound.CONNECT, BuiltInSound.SEATBELT_CHIME)
        store.useBuiltInSound(TripSound.DRIVING_OFF, BuiltInSound.RACE_START)

        val stored = file.data.first()
        assertEquals("seatbelt_chime", stored[stringPreferencesKey("connect_built_in_sound")])
        assertEquals("race_start", stored[stringPreferencesKey("driving_off_built_in_sound")])
    }

    @Test
    fun `a stored name this build does not know plays the first sound of the list`() = runTest {
        // What a later version's sound leaves behind, after a restore, an import or a step back.
        file.edit {
            it[stringPreferencesKey("connect_built_in_sound")] = "air_horn"
            it[stringPreferencesKey("driving_off_built_in_sound")] = ""
        }

        assertEquals(SoundChoice(true, null, null, BuiltInSound.CHIRP), plays(TripSound.CONNECT))
        assertEquals(
            SoundChoice(true, null, null, BuiltInSound.CHIME),
            plays(TripSound.DRIVING_OFF),
        )
        assertNull(store.current().connectBuiltIn)
        // The name is left in the file: the version that knows it plays it again.
        assertEquals("air_horn", file.data.first()[stringPreferencesKey("connect_built_in_sound")])
    }

    @Test
    fun `choosing a sound replaces a name this build did not know`() = runTest {
        file.edit { it[stringPreferencesKey("connect_built_in_sound")] = "air_horn" }

        store.useBuiltInSound(TripSound.CONNECT, BuiltInSound.COIN)

        assertEquals(BuiltInSound.COIN, plays(TripSound.CONNECT).builtIn)
        assertEquals("coin", file.data.first()[stringPreferencesKey("connect_built_in_sound")])
    }

    // ---- A sound of his own -----------------------------------------------------------------------

    @Test
    fun `a sound of his own is what plays, and if it cannot be, the first of the list`() = runTest {
        store.useBuiltInSound(TripSound.CONNECT, BuiltInSound.COIN)

        store.addOwnSound(TripSound.CONNECT, own, "r2d2.mp3")

        // Not the coin he had chosen before: his own sound took its place.
        assertEquals(
            SoundChoice(true, own, "r2d2.mp3", BuiltInSound.CHIRP),
            plays(TripSound.CONNECT),
        )
        assertNull(file.data.first()[stringPreferencesKey("connect_built_in_sound")])
    }

    @Test
    fun `his own sound gone again, the first of the list plays`() = runTest {
        store.useBuiltInSound(TripSound.DRIVING_OFF, BuiltInSound.BURNOUT)
        store.addOwnSound(TripSound.DRIVING_OFF, own, "mk64_racestart.mp3")

        store.removeOwnSound(own)

        assertEquals(
            SoundChoice(true, null, null, BuiltInSound.CHIME),
            plays(TripSound.DRIVING_OFF),
        )
    }

    @Test
    fun `a file that names both a sound of his own and a built-in one plays his own`() = runTest {
        // No setter writes the two together. A build from before 2026-10-13 that is handed a
        // newer file can: it knows only the first of these keys.
        file.edit {
            it[stringPreferencesKey("custom_sound_uri")] = own
            it[stringPreferencesKey("connect_built_in_sound")] = "coin"
        }

        assertEquals(SoundChoice(true, own, null, BuiltInSound.CHIRP), plays(TripSound.CONNECT))
    }

    @Test
    fun `a built-in sound chosen while his own plays takes its place, and his own stays listed`() =
        runTest {
            store.addOwnSound(TripSound.CONNECT, own, "r2d2.mp3")
            store.chooseOwnSound(TripSound.DRIVING_OFF, own)

            store.useBuiltInSound(TripSound.CONNECT, BuiltInSound.KEY_FOB)

            assertEquals(
                SoundChoice(true, null, null, BuiltInSound.KEY_FOB),
                plays(TripSound.CONNECT),
            )
            assertEquals(listOf(OwnSound(own, "r2d2.mp3")), store.current().ownSounds)
            // The other sound still plays it.
            assertEquals(own, plays(TripSound.DRIVING_OFF).ownUri)
        }

    @Test
    fun `a sound from the list chosen again forgets the built-in sound chosen meanwhile`() =
        runTest {
            store.addOwnSound(TripSound.CONNECT, own, "r2d2.mp3")
            store.useBuiltInSound(TripSound.CONNECT, BuiltInSound.SONAR_PING)

            assertTrue(store.chooseOwnSound(TripSound.CONNECT, own))
            store.removeOwnSound(own)

            assertEquals(BuiltInSound.CHIRP, plays(TripSound.CONNECT).builtIn)
        }

    // ---- The switches -----------------------------------------------------------------------------

    @Test
    fun `a switch silences its sound and keeps the choice, and leaves the other sound alone`() =
        runTest {
            store.useBuiltInSound(TripSound.CONNECT, BuiltInSound.TURN_SIGNAL)
            store.useBuiltInSound(TripSound.DRIVING_OFF, BuiltInSound.HORN_TAP)

            store.setSoundEnabled(TripSound.CONNECT, false)

            assertEquals(
                SoundChoice(false, null, null, BuiltInSound.TURN_SIGNAL),
                plays(TripSound.CONNECT),
            )
            assertEquals(
                SoundChoice(true, null, null, BuiltInSound.HORN_TAP),
                plays(TripSound.DRIVING_OFF),
            )

            store.setSoundEnabled(TripSound.CONNECT, true)
            assertEquals(BuiltInSound.TURN_SIGNAL, plays(TripSound.CONNECT).builtIn)
        }

    @Test
    fun `choosing a sound does not switch a silenced sound back on`() = runTest {
        store.setSoundEnabled(TripSound.DRIVING_OFF, false)

        store.useBuiltInSound(TripSound.DRIVING_OFF, BuiltInSound.ENGINE_REV)

        assertFalse(plays(TripSound.DRIVING_OFF).enabled)
    }

    // ---- A MilO that is updated ---------------------------------------------------------------------

    @Test
    fun `a file from before there was a choice reads as it always did`() = runTest {
        // Everything version 0.3.0 could have stored about the two sounds: the switches, a
        // sound of his own for the trip-start sound, and the list.
        file.edit {
            it[booleanPreferencesKey("sound_enabled")] = false
            it[booleanPreferencesKey("driving_off_sound_enabled")] = true
            it[stringPreferencesKey("driving_off_sound_uri")] = own
            it[stringPreferencesKey("driving_off_sound_name")] = "mk64_racestart.mp3"
            it[stringSetPreferencesKey("own_sounds")] = setOf("$own\tmk64_racestart.mp3")
        }

        // The connect sound is still the chirp, and still switched off.
        assertEquals(SoundChoice(false, null, null, BuiltInSound.CHIRP), plays(TripSound.CONNECT))
        // The trip-start sound still plays his own, with the chime behind it as before.
        assertEquals(
            SoundChoice(true, own, "mk64_racestart.mp3", BuiltInSound.CHIME),
            plays(TripSound.DRIVING_OFF),
        )
        assertEquals(listOf(OwnSound(own, "mk64_racestart.mp3")), store.current().ownSounds)
        // And reading it wrote nothing: the file holds what it held.
        assertEquals(5, file.data.first().asMap().size)
    }

    // ---- After a restore ----------------------------------------------------------------------------

    @Test
    fun `after a restore a chosen built-in sound stays, and his own gives way to the first`() =
        runTest {
            store.useBuiltInSound(TripSound.CONNECT, BuiltInSound.COUNTER_BELL)
            store.addOwnSound(TripSound.DRIVING_OFF, own, "mk64_racestart.mp3")

            // The copies of his own sounds are in no backup: they are not on this phone.
            store.forgetOtherInstallation(dropAssociation = false, dropOwnSound = true)

            assertEquals(
                SoundChoice(true, null, null, BuiltInSound.COUNTER_BELL),
                plays(TripSound.CONNECT),
            )
            assertEquals(
                SoundChoice(true, null, null, BuiltInSound.CHIME),
                plays(TripSound.DRIVING_OFF),
            )
            assertTrue(store.current().ownSounds.isEmpty())
        }
}
