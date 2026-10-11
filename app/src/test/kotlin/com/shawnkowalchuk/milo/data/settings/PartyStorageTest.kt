package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Home still owes of MilO's one-year party, as the settings file keeps it. */
class PartyStorageTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)

    @Test
    fun `out of the box nothing is owed`() = runTest {
        assertNull(store.current().partyOwedSinceMs)
        assertEquals(MiloSettings(), store.current())
    }

    @Test
    fun `a trip's start is stored, and a later trip's takes its place`() = runTest {
        store.setPartyOwedSinceMs(1_823_000_000_000L)
        assertEquals(1_823_000_000_000L, store.current().partyOwedSinceMs)

        store.setPartyOwedSinceMs(1_823_000_900_000L)

        assertEquals(1_823_000_900_000L, store.current().partyOwedSinceMs)
        // Nothing else in the file is touched.
        assertEquals(MiloSettings(partyOwedSinceMs = 1_823_000_900_000L), store.current())
    }

    @Test
    fun `it is taken out when the party was shown for that trip`() = runTest {
        store.setPartyOwedSinceMs(1_823_000_000_000L)

        store.clearPartyOwed(shownForMs = 1_823_000_000_000L)

        assertNull(store.current().partyOwedSinceMs)
    }

    @Test
    fun `a trip that started since the showing began stays owed`() = runTest {
        store.setPartyOwedSinceMs(1_823_000_000_000L)
        // The party plays for the first trip, and a second starts before that is stored.
        store.setPartyOwedSinceMs(1_823_000_004_000L)

        store.clearPartyOwed(shownForMs = 1_823_000_000_000L)

        assertEquals(1_823_000_004_000L, store.current().partyOwedSinceMs)
    }

    @Test
    fun `null takes it out, and taking out what is not there does nothing`() = runTest {
        store.setPartyOwedSinceMs(1_823_000_000_000L)
        store.setPartyOwedSinceMs(null)
        assertNull(store.current().partyOwedSinceMs)

        store.clearPartyOwed(shownForMs = 1_823_000_000_000L)

        assertEquals(MiloSettings(), store.current())
    }

    @Test
    fun `a negative moment is refused, and one that got into the file is read as none`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.setPartyOwedSinceMs(-1L) }
        }
        file.edit { it[longPreferencesKey("party_owed_since_ms")] = -5L }

        assertNull(store.current().partyOwedSinceMs)
    }

    @Test
    fun `the pop is heard while either of MilO's sounds is switched on`() = runTest {
        assertTrue(store.current().partyPopHeard)

        store.setSoundEnabled(TripSound.CONNECT, false)
        assertTrue(store.current().partyPopHeard)
        store.setSoundEnabled(TripSound.DRIVING_OFF, false)
        assertFalse(store.current().partyPopHeard)
        store.setSoundEnabled(TripSound.CONNECT, true)
        assertTrue(store.current().partyPopHeard)
    }
}
