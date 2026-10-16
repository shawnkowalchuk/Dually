package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.service.ServiceRecord
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.changeServiceBook
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Done" pressed on Home's tile for a service item. */
class ServiceDoneTest {
    private val store = SettingsStore(FakeSettingsFile())
    private val trips = TripRepository(FakeTripDao())

    @Test
    fun `it records now and counts the next one from here`() = runTest {
        store.changeServiceBook {
            it.saved(ServiceItem(0, null, "Oil change", 8_000, 6, 1_000L, 120_000))
        }

        markServiceDone(store, trips, itemId = 1, nowMs = 5_000L, zone = ZoneOffset.UTC)

        val book = store.current().serviceBook
        // No odometer reading here: the last one is kept.
        assertEquals(listOf(ServiceRecord(1, 5_000L, null)), book.records)
        assertEquals(5_000L, book.items.single().lastDoneAtMs)
        assertEquals(120_000L, book.items.single().lastDoneKm)
    }

    @Test
    fun `an item no longer in the book is left alone`() = runTest {
        markServiceDone(store, trips, itemId = 7, nowMs = 5_000L, zone = ZoneOffset.UTC)

        assertEquals(emptyList<ServiceRecord>(), store.current().serviceBook.records)
    }
}
