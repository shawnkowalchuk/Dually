package com.shawnkowalchuk.milo.platform.service

import com.shawnkowalchuk.milo.core.service.ServiceAlert
import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.service.ServiceRecord
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.changeServiceBook
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The look the service reminder takes when a trip has ended. */
class ServiceReminderTest {
    private val zone = ZoneOffset.UTC
    private val nowMs = LocalDate.of(2026, 10, 16).atStartOfDay(zone).toInstant().toEpochMilli()
    private val januaryMs = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    private val store = SettingsStore(FakeSettingsFile())
    private val log = FakeEventLogDao()
    private val told = mutableListOf<List<String>>()
    private val reminder =
        ServiceReminder(
            settings = store,
            trips = TripRepository(FakeTripDao()),
            notify = { alerts: List<ServiceAlert>, _ -> told += alerts.map { it.item.name } },
            eventLog = EventLogRepository(log),
            clock = { nowMs },
            zone = { zone },
        )

    @Test
    fun `an item that has come due is told once, and again after Done when it is due again`() =
        runTest {
            store.changeServiceBook {
                it.saved(ServiceItem(0, null, "Oil change", null, 6, januaryMs, null))
            }

            reminder.look()
            reminder.look()
            assertEquals(listOf(listOf("Oil change")), told)
            assertEquals(1, log.entries.count { "Oil change" in it.message })

            // Done back in March: due again since September, a new due point.
            store.changeServiceBook {
                val march = LocalDate.of(2026, 3, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                it.done(ServiceRecord(1, march, null))
            }
            reminder.look()
            assertEquals(2, told.size)
        }

    @Test
    fun `nothing soon or due is no notification`() = runTest {
        store.changeServiceBook {
            it.saved(ServiceItem(0, null, "Inspection", null, 12, nowMs, null))
        }

        reminder.look()

        assertEquals(emptyList<List<String>>(), told)
    }
}
