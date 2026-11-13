package com.shawnkowalchuk.milo.core.service

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceDueTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val lastDone = LocalDate.of(2026, 4, 16)
    private val lastDoneMs = lastDone.atStartOfDay(zone).toInstant().toEpochMilli()
    private val oil = ServiceItem(1, null, "Oil change", 8_000, 6, lastDoneMs, 120_000)

    private fun on(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `far from both is fine`() {
        val due = serviceDue(oil, 122_000, on(LocalDate.of(2026, 6, 1)), zone)

        assertEquals(DueState.OK, due.state)
        assertEquals(6_000L, due.kmLeft)
        assertEquals(LocalDate.of(2026, 10, 16), due.dueOn)
    }

    @Test
    fun `soon within 800 km, a tenth of 8,000, or two weeks`() {
        assertEquals(
            DueState.SOON,
            serviceDue(oil, 127_200, on(LocalDate.of(2026, 6, 1)), zone).state,
        )
        assertEquals(
            DueState.OK,
            serviceDue(oil, 127_100, on(LocalDate.of(2026, 6, 1)), zone).state,
        )
        assertEquals(
            DueState.SOON,
            serviceDue(oil, 121_000, on(LocalDate.of(2026, 10, 2)), zone).state,
        )
    }

    @Test
    fun `a short interval is soon within 500 km`() {
        val short = oil.copy(everyKm = 2_000)
        assertEquals(
            DueState.SOON,
            serviceDue(short, 121_500, on(LocalDate.of(2026, 5, 1)), zone).state,
        )
    }

    @Test
    fun `due at whichever comes first`() {
        val byDistance = serviceDue(oil, 128_400, on(LocalDate.of(2026, 6, 1)), zone)
        assertEquals(DueState.DUE, byDistance.state)
        assertEquals(-400L, byDistance.kmLeft)

        val byTime = serviceDue(oil, 121_000, on(LocalDate.of(2026, 10, 16)), zone)
        assertEquals(DueState.DUE, byTime.state)
        assertEquals(0L, byTime.daysLeft)
    }

    @Test
    fun `with no odometer reading only the time counts`() {
        val due = serviceDue(oil, null, on(LocalDate.of(2026, 6, 1)), zone)
        assertNull(due.kmLeft)
        assertEquals(DueState.OK, due.state)

        val byDistanceOnly = oil.copy(everyMonths = null)
        assertEquals(
            DueState.OK,
            serviceDue(byDistanceOnly, null, on(LocalDate.of(2030, 1, 1)), zone).state,
        )
    }
}
