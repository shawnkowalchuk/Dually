package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.util.AppLook
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The look of the screens and the widget (since 2026-11-12): one stored setting, Dark until picked. */
class LookStorageTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)
    private val key = stringPreferencesKey("look")

    @Test
    fun `out of the box the look is dark, and nothing is stored`() = runTest {
        assertEquals(AppLook.DARK, store.current().look)
        assertNull(file.data.first()[key])
    }

    @Test
    fun `light is stored, and dark again, and nothing else is touched`() = runTest {
        store.setReportName("Sam Driver")
        val before = store.current()

        store.setLook(AppLook.LIGHT)

        assertEquals(before.copy(look = AppLook.LIGHT), store.current())

        store.setLook(AppLook.DARK)

        assertEquals(before, store.current())
    }

    @Test
    fun `the words the look is stored under never change`() = runTest {
        store.setLook(AppLook.LIGHT)
        assertEquals("light", file.data.first()[key])
        store.setLook(AppLook.DARK)
        assertEquals("dark", file.data.first()[key])
        assertEquals(AppLook.entries.toSet(), setOf("dark", "light").map(::appLookOf).toSet())
    }

    @Test
    fun `a word Dually could not have written is read as dark`() = runTest {
        file.edit { it[key] = "sepia" }

        assertEquals(AppLook.DARK, store.current().look)
    }
}
