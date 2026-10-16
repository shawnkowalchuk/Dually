package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What Shawn chose on Home for the trip being recorded, as the settings file keeps it. */
class RunningTripCategoryStorageTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)

    @Test
    fun `a choice is stored with its trip, replaced, and taken out`() = runTest {
        assertNull(store.current().runningTripCategory)

        store.setRunningTripCategory(12, TripCategory.PERSONAL)
        assertEquals(
            RunningTripCategory(12, TripCategory.PERSONAL),
            store.current().runningTripCategory,
        )

        store.setRunningTripCategory(12, TripCategory.BUSINESS)
        assertEquals(
            RunningTripCategory(12, TripCategory.BUSINESS),
            store.current().runningTripCategory,
        )

        store.setRunningTripCategory(0, null)
        assertNull(store.current().runningTripCategory)
    }

    @Test
    fun `an entry that is not of the form is none`() = runTest {
        for (stored in listOf("12", "x:BUSINESS", "12:WORK")) {
            file.edit { it[stringPreferencesKey("running_trip_category")] = stored }
            assertNull(stored, store.current().runningTripCategory)
        }
    }
}
