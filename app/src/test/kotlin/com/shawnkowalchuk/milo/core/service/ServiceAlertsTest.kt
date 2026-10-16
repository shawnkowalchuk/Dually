package com.shawnkowalchuk.milo.core.service

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceAlertsTest {
    private val zone = ZoneOffset.UTC
    private val lastDoneMs = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val nowMs = LocalDate.of(2026, 10, 16).atStartOfDay(zone).toInstant().toEpochMilli()

    private val book =
        ServiceBook()
            // Due by time: six months from January.
            .saved(ServiceItem(0, null, "Oil change", null, 6, lastDoneMs, null))
            // Fine: every 12 months.
            .saved(ServiceItem(0, null, "Inspection", null, 12, lastDoneMs, null))
            // Soon by distance: 300 km left.
            .saved(ServiceItem(0, null, "Brakes", 10_000, null, lastDoneMs, 100_000))
            // Due by distance.
            .saved(ServiceItem(0, null, "air filter", 5_000, null, lastDoneMs, 100_000))

    private fun alerts(b: ServiceBook = book) = serviceAlerts(b, { 109_700L }, nowMs, zone)

    @Test
    fun `soon and due items, the due ones first, each by name`() {
        assertEquals(
            listOf("air filter", "Oil change", "Brakes"),
            alerts().map { it.item.name },
        )
    }

    @Test
    fun `each due point is told once, and again after Done`() {
        val told = book.told(alerts())
        assertEquals(emptyList<ServiceAlert>(), told.untold(alerts(told)))

        // Done today at 109,700 km: the oil change is due again only in six months.
        val done = told.done(ServiceRecord(1, nowMs, 109_700))
        assertEquals(listOf("air filter", "Brakes"), alerts(done).map { it.item.name })
        assertEquals(emptyList<ServiceAlert>(), done.untold(alerts(done)))

        // A changed last-done day is a new due point.
        val edited = done.saved(
            done.items.first {
                it.id == 4L
            }.copy(lastDoneAtMs = lastDoneMs + 1),
        )
        assertEquals(listOf("air filter"), edited.untold(alerts(edited)).map { it.item.name })
    }

    @Test
    fun `an item goes by its own vehicle while it is paired, or else the first`() {
        val item = ServiceItem(1, "BB", "Oil change", 8_000, null, 0L, 0)
        assertEquals("bb", vehicleOf(item, listOf("AA", "bb")))
        assertEquals("AA", vehicleOf(item.copy(vehicle = "CC"), listOf("AA", "BB")))
        assertEquals("AA", vehicleOf(item.copy(vehicle = null), listOf("AA")))
        assertNull(vehicleOf(item, emptyList()))
    }
}
