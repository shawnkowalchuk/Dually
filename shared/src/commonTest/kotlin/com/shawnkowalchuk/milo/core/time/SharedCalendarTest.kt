package com.shawnkowalchuk.milo.core.time

import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.daysSpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val HOUR_MS = 60L * 60L * 1000L
private const val DAY_MS = 24L * HOUR_MS

/** 2025-11-13 07:05 in New York, where the clocks went back on 2 November 2025. */
private const val MORNING_MS = 1_763_035_500_000L

/**
 * The shared calendar (core/time, ADR-014 phase 3) answers alike on Android, where it is
 * java.time, and on the iPhone, where it is kotlinx-datetime: these run on both. New York's
 * clock changes of 2025 are used because every system's zone data agrees on them.
 */
class SharedCalendarTest {
    private val newYork = testZone("America/New_York")

    @Test
    fun `a moment is put on its day and its month in the zone`() {
        assertEquals(dateOf(2025, 11, 13), localDateOf(MORNING_MS, newYork))
        assertEquals("2025-11", monthOf(MORNING_MS, newYork).toString())
        assertEquals("2025-11-13", formatIsoDay(MORNING_MS, newYork))
        assertEquals("07:05", formatIsoTime(MORNING_MS, newYork))
        assertEquals("19:05", formatIsoTime(MORNING_MS + 12 * HOUR_MS, newYork))
    }

    @Test
    fun `a day on which the clocks change is as long as it really is`() {
        val spring = daySpan(dateOf(2025, 3, 9), newYork)
        assertEquals(1_741_496_400_000L, spring.fromMs)
        assertEquals(23 * HOUR_MS, spring.untilMs - spring.fromMs)
        val autumn = daySpan(dateOf(2025, 11, 2), newYork)
        assertEquals(25 * HOUR_MS, autumn.untilMs - autumn.fromMs)
    }

    @Test
    fun `a month runs from its first midnight to the next month's`() {
        val november = monthSpan(monthOf(MORNING_MS, newYork), newYork)
        assertEquals(1_761_969_600_000L, november.fromMs)
        assertEquals(30 * DAY_MS + HOUR_MS, november.untilMs - november.fromMs)
    }

    @Test
    fun `a run of days includes its last day and cannot end before it starts`() {
        val span = daysSpan(dateOf(2025, 11, 12), dateOf(2025, 11, 13), newYork)
        assertEquals(2 * DAY_MS, span.untilMs - span.fromMs)
        assertFailsWith<IllegalArgumentException> {
            daysSpan(dateOf(2025, 11, 13), dateOf(2025, 11, 12), newYork)
        }
    }

    @Test
    fun `days and months and years move as java_time moves them`() {
        assertEquals(dateOf(2026, 3, 1), dateOf(2026, 2, 28).addDays(1))
        assertEquals(dateOf(2026, 2, 28), dateOf(2026, 1, 31).addMonths(1))
        assertEquals(dateOf(2025, 2, 28), dateOf(2024, 2, 29).addYears(1))
        assertEquals(dateOf(2028, 2, 29), dateOf(2024, 2, 29).addYears(4))
        assertEquals(366L, dateOf(2024, 1, 1).daysUntil(dateOf(2025, 1, 1)))
        assertEquals(-1L, dateOf(2025, 1, 1).daysUntil(dateOf(2024, 12, 31)))
        assertTrue(compareDates(dateOf(2025, 1, 1), dateOf(2025, 1, 2)) < 0)
        assertEquals(2025, dateOf(2025, 6, 1).yearNumber())
        assertEquals("2025-06-01", dateOf(2025, 6, 1).toString())
    }

    @Test
    fun `a month knows its first and last day`() {
        val february = monthOf(1_834_246_800_000L, newYork)
        assertEquals("2028-02", february.toString())
        assertEquals(dateOf(2028, 2, 1), february.firstDay())
        assertEquals(dateOf(2028, 2, 29), february.lastDay())
    }

    @Test
    fun `times of day compare and count whole minutes`() {
        assertTrue(compareTimes(timeOf(8, 0), timeOf(16, 30)) < 0)
        assertEquals(0, compareTimes(timeOf(8, 0), timeOf(8, 0)))
        assertEquals(30_600, timeOf(8, 30).secondsIntoDay())
        assertTrue(timeOf(23, 59).isWholeMinute())
    }
}
