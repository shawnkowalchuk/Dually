package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.service.ServiceBook
import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.service.ServiceRecord
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The service book as the settings file keeps it. */
class ServiceStorageTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)

    private val oil = ServiceItem(0, "AA:BB", "Oil change", 8_000, 6, 1_000L, 120_000)

    @Test
    fun `a book is stored and read back as it was`() = runTest {
        assertEquals(ServiceBook(), store.current().serviceBook)

        store.changeServiceBook { it.saved(oil) }
        store.changeServiceBook { it.done(ServiceRecord(1, 2_000L, 128_000, "Synthetic", 8_999)) }

        val book = store.current().serviceBook
        assertEquals(
            listOf(oil.copy(id = 1, lastDoneAtMs = 2_000L, lastDoneKm = 128_000)),
            book.items,
        )
        assertEquals(listOf(ServiceRecord(1, 2_000L, 128_000, "Synthetic", 8_999)), book.records)
    }

    @Test
    fun `a book that cannot be read is an empty one, and the screen still opens`() = runTest {
        for (stored in listOf("", "{", "[1,2]", "{\"items\":\"no\"}")) {
            file.edit { it[stringPreferencesKey("service_book")] = stored }
            assertEquals(stored, ServiceBook(), store.current().serviceBook)
        }
    }

    @Test
    fun `a field a later version adds is passed over`() = runTest {
        file.edit {
            it[stringPreferencesKey("service_book")] =
                "{\"items\":[],\"records\":[],\"remindedFor\":{},\"comingLater\":1}"
        }
        assertEquals(ServiceBook(), store.current().serviceBook)
    }
}
