package com.shawnkowalchuk.milo.core.time

import com.shawnkowalchuk.milo.core.allowance.centsPerMileOf
import com.shawnkowalchuk.milo.core.allowance.formatCentsPerKm
import com.shawnkowalchuk.milo.core.allowance.formatWholeDollars
import com.shawnkowalchuk.milo.core.number.decimalOf
import com.shawnkowalchuk.milo.core.text.formatText
import com.shawnkowalchuk.milo.core.text.rootLocale
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatDayOfWeek
import com.shawnkowalchuk.milo.core.util.formatFullMonthName
import com.shawnkowalchuk.milo.core.util.formatLogTime
import com.shawnkowalchuk.milo.core.util.formatLongDay
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear
import com.shawnkowalchuk.milo.core.util.formatShortDayOfWeek
import com.shawnkowalchuk.milo.core.util.formatShortDistance
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.core.util.metresOf
import com.shawnkowalchuk.milo.core.util.metresOfTenths
import com.shawnkowalchuk.milo.core.util.monthOf
import com.shawnkowalchuk.milo.core.util.tenthsOf
import com.shawnkowalchuk.milo.core.util.tenthsOfWhole
import com.shawnkowalchuk.milo.core.util.usesTwelveHourClock
import com.shawnkowalchuk.milo.core.util.wholeUnitsBelow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 2025-11-13 07:05 in New York. */
private const val MORNING_MS = 1_763_035_500_000L

/** Newer language data writes a narrow space before AM; the tests read every space alike. */
private fun String.spaced(): String = replace(' ', ' ').replace(' ', ' ')

/**
 * The shared words and figures (ADR-014 phase 3) come out alike on Android and on the iPhone:
 * these run on both. Where a form is the language's own, the iPhone's is Apple's; English
 * (United States) is written the same by both, so it is what is checked here.
 */
class SharedWordsTest {
    private val english = testLocale("en-US")
    private val newYork = testZone("America/New_York")

    @Test
    fun `numbers are filled in as Java's String format fills them in`() {
        assertEquals("1,234,567", formatText(english, "%,d", 1_234_567L))
        assertEquals("12", formatText(english, "%d", 12))
        assertEquals("2.3", formatText(english, "%.1f", 2.25))
        assertEquals("2.68", formatText(english, "%.2f", 2.675))
        assertEquals("b then a", formatText(english, "%2\$s then %1\$s", "a", "b"))
        assertEquals("50% of 7", formatText(english, "%d%% of %s", 50, 7))
        assertEquals("1234.5", formatText(rootLocale, "%.1f", 1234.5))
    }

    @Test
    fun `distances in kilometres and miles round as they always have`() {
        assertEquals(12_346L, tenthsOf(1_234_560.0, DistanceUnit.KILOMETRES))
        assertEquals(10L, tenthsOf(1_609.344, DistanceUnit.MILES))
        assertEquals(1L, tenthsOf(80.4672, DistanceUnit.MILES))
        assertEquals("1234.5", formatTenths(12_345L, english))
        assertEquals("2.35", formatShortDistance(3_773.91168, DistanceUnit.MILES, english))
        assertEquals(1L, wholeUnitsBelow(3_218.0, DistanceUnit.MILES))
        assertEquals(2_500.0, metresOf(decimalOf("2.5"), DistanceUnit.KILOMETRES))
        assertEquals(1_609.344, metresOfTenths(10L, DistanceUnit.MILES))
        assertEquals(1_609L, tenthsOfWhole(100L, DistanceUnit.MILES, DistanceUnit.KILOMETRES))
        assertEquals(1_000L, tenthsOfWhole(100L, DistanceUnit.MILES, DistanceUnit.MILES))
    }

    @Test
    fun `the allowance is worked out and written as before`() {
        assertEquals(113, centsPerMileOf(70))
        assertEquals("$0.70", formatCentsPerKm(70, english))
        assertEquals("$1,235", formatWholeDollars(123_456L, english))
    }

    @Test
    fun `days are written in the language's own forms`() {
        val day = dateOf(2025, 11, 13)
        assertEquals("Thursday, November 13, 2025", formatDay(day, english))
        assertEquals("Nov 13, 2025", formatMediumDay(day, english))
        assertEquals("November 13, 2025", formatLongDay(day, english))
        assertEquals("November 2025", formatMonthAndYear(monthOf(MORNING_MS, newYork), english))
        assertEquals("November", formatFullMonthName(monthOf(MORNING_MS, newYork), english))
        assertEquals("Thursday", formatDayOfWeek(DayOfWeek.THURSDAY, english))
        assertEquals("Mon", formatShortDayOfWeek(DayOfWeek.MONDAY, english))
        assertEquals("Sun", formatShortDayOfWeek(DayOfWeek.SUNDAY, english))
    }

    @Test
    fun `times follow the clock the phone is set to`() {
        assertTrue(usesTwelveHourClock(english))
        assertFalse(usesTwelveHourClock(testLocale("de-DE")))
        assertEquals("7:05 AM", formatTimeOfDay(MORNING_MS, newYork, english, false).spaced())
        assertEquals("07:05", formatTimeOfDay(MORNING_MS, newYork, english, true))
        assertEquals("2025-11-13 07:05:00", formatLogTime(MORNING_MS, newYork))
    }
}
