package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The sent months whose GPS points Shawn chose to keep, as the settings file keeps them. */
class PointsKeptStorageTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)

    @Test
    fun `out of the box no month is kept`() = runTest {
        assertEquals(emptySet<YearMonth>(), store.current().pointsKeptMonths)
    }

    @Test
    fun `each month kept is added, and nothing else in the file is touched`() = runTest {
        store.keepPointsOf(YearMonth.of(2026, 9))
        store.keepPointsOf(YearMonth.of(2026, 8))
        store.keepPointsOf(YearMonth.of(2026, 9))

        val months = setOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9))
        assertEquals(MiloSettings(pointsKeptMonths = months), store.current())
    }

    @Test
    fun `an entry that is not a month is skipped`() = runTest {
        file.edit { it[stringSetPreferencesKey("points_kept_months")] = setOf("2026-09", "Sept") }

        assertEquals(setOf(YearMonth.of(2026, 9)), store.current().pointsKeptMonths)
    }
}
